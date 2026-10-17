// 결제 회복탄력성 측정 k6 스크립트. MODE 로 네 측정 중 하나를 고른다.
//
// k6 v2.1.0 대상. ab.js 의 관례를 따른다 — 코어 모듈만 쓰고(k6/http, k6/metrics, k6/execution, k6),
// 옵셔널 체이닝(?.)을 쓰지 않고, 판정용 서브메트릭은 threshold 를 걸어 summary 에 나오게 한다.
//
// 설계 문서: docs/superpowers/specs/2026-10-13-payment-design.md 11 장 (판정 기준은 11.3 장 표)
//
//   MODE=pg-direct  P0   — PG 를 commerce-api 없이 직접 불러 500 비율을 잰다 (37 ~ 43% 이어야 한다)
//   MODE=normal     가설 1 — 결제 10 rps 를 길게, 서킷이 한 번도 열리지 않아야 한다
//   MODE=pg-down    가설 2 — 프록시를 끈 채 결제. opening(앞 15 s) 과 open(뒤 60 s) 으로 나눠 집계
//   MODE=mixed      가설 3 — 주문 상세 읽기 100 rps (+ 결제 70 rps). PAYMENT_TPS=0 이면 읽기 단독(A)
//
// 사용: MODE=<mode> LABEL=<label> [PAYMENT_TPS] [READ_TPS] [DURATION] [WARMUP] k6 run loadtest/payment.js
// 결과: loadtest/results/payment-<MODE>-<LABEL>.json

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

// ── 환경변수 ────────────────────────────────────────────────────────────
const MODE = __ENV.MODE || '';
const LABEL = __ENV.LABEL || 'run';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PG_URL = __ENV.PG_URL || 'http://localhost:8082';

// 모드마다 기본값이 달라 한 번에 표로 둔다. (설계 문서 11.3 장)
//   tps       결제(또는 PG 직접) 도착률
//   duration  측정 구간 길이 (pg-down 은 open 구간 길이)
//   warmup    측정 앞의 워밍업 길이. 0s 면 시나리오를 두지 않는다
const MODE_DEFAULTS = {
    'pg-direct': { tps: 20, duration: '50s', warmup: '0s' },
    normal: { tps: 10, duration: '10m', warmup: '0s' },
    'pg-down': { tps: 10, duration: '60s', warmup: '0s' },
    mixed: { tps: 70, duration: '120s', warmup: '10s' },
};

if (!MODE_DEFAULTS[MODE]) {
    throw new Error('MODE 는 pg-direct | normal | pg-down | mixed 중 하나여야 한다. 받은 값: "' + MODE + '"');
}

const DEFAULTS = MODE_DEFAULTS[MODE];
const PAYMENT_TPS = Number(__ENV.PAYMENT_TPS !== undefined ? __ENV.PAYMENT_TPS : DEFAULTS.tps);
const READ_TPS = Number(__ENV.READ_TPS || 100);
const DURATION = __ENV.DURATION || DEFAULTS.duration;
const WARMUP = __ENV.WARMUP || DEFAULTS.warmup;
const OPENING = __ENV.OPENING || '15s'; // pg-down 에서 서킷이 열리기 전 구간

// LocalDataSeeder 가 넣는 시드 데이터 (apps/commerce-api/.../support/seed/LocalDataSeeder.kt)
const SEED_USERS = ['seeduser01', 'seeduser02', 'seeduser03'];
const PRODUCT_COUNT = 137;
const CARD_TYPE = 'SAMSUNG';
const CARD_NO = '1234-5678-9814-1451';

// ── VU 풀 크기 ──────────────────────────────────────────────────────────
// ab.js 와 같은 근거다. 필요 VU 는 도착률 × 응답시간이고, 부족하면 k6 가 반복을 버려(dropped_iterations)
// "서버 한계인지 VU 부족인지" 가려지지 않는다. preAllocated 를 넘으면 VU 를 그 자리에서 만드는 비용이
// 도착률을 흔들므로 미리 넉넉히 만든다. 결제 한 반복은 요청이 둘(주문 → 결제)이라 응답시간이 길다.
function preAllocated(rps) {
    return Math.max(50, rps * 3);
}
function maxOf(rps) {
    return Math.max(preAllocated(rps), rps * 10);
}

// ── 커스텀 메트릭 ───────────────────────────────────────────────────────
// 결제 응답의 data.status 별 건수. 워밍업 반복은 세지 않는다 — 판정 구간과 섞이면 "REQUESTED 가 한 건도 없다"
// 같은 판정이 워밍업에 오염된다.
const paymentPending = new Counter('payment_pending');
const paymentRequested = new Counter('payment_requested');
const paymentFailed = new Counter('payment_failed');
const paymentSuccess = new Counter('payment_success');
const paymentError = new Counter('payment_error'); // 주문 생성 실패 포함, 200 이 아닌 응답
const pg200 = new Counter('pg_200');
const pg500 = new Counter('pg_500');
const pgOther = new Counter('pg_other');

// ── 시나리오 ────────────────────────────────────────────────────────────
function arrival(rate, duration, startTime, execName, extraTags) {
    return {
        executor: 'constant-arrival-rate', // 도착률 고정. 서버가 느려져도 부하가 줄지 않아야 한계가 드러난다.
        rate: rate,
        timeUnit: '1s',
        duration: duration,
        preAllocatedVUs: preAllocated(rate),
        maxVUs: maxOf(rate),
        exec: execName,
        startTime: startTime,
        tags: extraTags || {},
    };
}

function hasWarmup() {
    return parseDurationSeconds(WARMUP) > 0;
}

function buildScenarios() {
    const s = {};
    if (MODE === 'pg-direct') {
        s.direct = arrival(PAYMENT_TPS, DURATION, '0s', 'pgDirect');
    } else if (MODE === 'normal') {
        s.payment = arrival(PAYMENT_TPS, DURATION, '0s', 'pay');
    } else if (MODE === 'pg-down') {
        // 둘 다 같은 결제 반복이다. 이름으로 나눠 집계하는 것이 목적이다 — 서킷이 열리기 전(opening)의
        // 느린 요청이 열린 뒤(open)의 p99 에 섞이면 "열리면 빠르다" 는 가설을 못 잰다.
        s.opening = arrival(PAYMENT_TPS, OPENING, '0s', 'pay');
        s.open = arrival(PAYMENT_TPS, DURATION, OPENING, 'pay');
    } else {
        // mixed: 읽기는 항상, 결제는 PAYMENT_TPS > 0 일 때만. 0 이면 가설 3 의 A(결제 없는 기준선)다.
        // 워밍업은 별도 시나리오로 앞에 두어 측정 시나리오(read · payment)의 서브메트릭과 섞이지 않게 한다.
        if (hasWarmup()) {
            s['warmup-read'] = arrival(READ_TPS, WARMUP, '0s', 'readOrder');
            if (PAYMENT_TPS > 0) {
                s['warmup-payment'] = arrival(PAYMENT_TPS, WARMUP, '0s', 'pay');
            }
        }
        s.read = arrival(READ_TPS, DURATION, WARMUP, 'readOrder');
        if (PAYMENT_TPS > 0) {
            s.payment = arrival(PAYMENT_TPS, DURATION, WARMUP, 'pay');
        }
    }
    return s;
}

// 판정에 쓰는 서브메트릭. threshold 를 걸어야 summary 의 data.metrics 에 '메트릭{태그}' 키로 노출된다.
// 'p(99)<100000' 은 사실상 통과하는 기록용이고, 진짜 판정은 설계 11.3 장 표를 사람이 본다 — 단 하나,
// 가설 2 의 open 구간 p99 ≤ 50 ms 와 도착률 누락은 여기서도 건다.
function durKey(name, scenario) {
    return 'http_req_duration{name:' + name + ',scenario:' + scenario + '}';
}

function buildThresholds() {
    const t = {};
    t.dropped_iterations = ['count==0']; // 도착률을 못 맞춘 측정은 판정하지 않는다 (설계 문서 11.3 장)
    if (MODE === 'pg-direct') {
        t[durKey('pg-direct', 'direct')] = ['p(99)<100000'];
    } else if (MODE === 'normal') {
        t[durKey('payment', 'payment')] = ['p(99)<100000'];
    } else if (MODE === 'pg-down') {
        t[durKey('payment', 'opening')] = ['p(99)<100000'];
        t[durKey('payment', 'open')] = ['p(99)<=50']; // 가설 2 (설계 문서 11.3 장)
    } else {
        t[durKey('order-detail', 'read')] = ['p(99)<100000'];
        if (PAYMENT_TPS > 0) {
            t[durKey('payment', 'payment')] = ['p(99)<100000'];
        }
    }
    return t;
}

export const options = {
    scenarios: buildScenarios(),
    thresholds: buildThresholds(),
    // 기본 요약에는 p(99) 가 없다. 판정이 p99 이므로 반드시 켠다.
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    // abortOnFail 을 쓰지 않는다. 임계값을 넘겨도 끝까지 돌려야 얼마나 넘었는지 안다 (ab.js 와 같은 이유).
};

// 읽기 대상 주문을 회원마다 하나씩 만든다. 읽기 부하가 같은 행을 두드려 DB 가 아니라 버퍼 캐시를 재는 것을
// 피하려면 주문이 많은 편이 낫지만, 주문 상세는 PK 조회라 행 수보다 "매 요청이 커넥션을 잡는다" 가 핵심이다.
// (설계 문서 11.1 장 가설 3)
export function setup() {
    if (MODE !== 'mixed') {
        return {};
    }
    const orderIds = [];
    for (let i = 0; i < SEED_USERS.length; i++) {
        const res = http.post(
            BASE_URL + '/api/v1/orders',
            JSON.stringify({ items: [{ productId: 1, quantity: 1 }] }),
            { headers: headers(SEED_USERS[i]), tags: { name: 'setup-order' } },
        );
        if (res.status !== 200) {
            throw new Error('setup 주문 생성 실패: user=' + SEED_USERS[i] + ' status=' + res.status + ' body=' + res.body);
        }
        orderIds.push(res.json('data.id'));
    }
    return { orderIds: orderIds };
}

function headers(user) {
    return { 'Content-Type': 'application/json', 'X-Loopers-LoginId': user };
}

function userIndex() {
    return exec.vu.idInInstance % SEED_USERS.length;
}

function isWarmup() {
    return exec.scenario.name.indexOf('warmup') === 0;
}

// 결제 반복: 주문 생성 → 결제 요청.
export function pay() {
    const user = SEED_USERS[userIndex()];
    const counting = !isWarmup();

    const orderRes = http.post(
        BASE_URL + '/api/v1/orders',
        JSON.stringify({ items: [{ productId: Math.floor(Math.random() * PRODUCT_COUNT) + 1, quantity: 1 }] }),
        { headers: headers(user), tags: { name: 'order' } },
    );
    if (orderRes.status !== 200) {
        if (counting) {
            paymentError.add(1);
        }
        return;
    }

    const payRes = http.post(
        BASE_URL + '/api/v1/payments',
        JSON.stringify({ orderId: orderRes.json('data.id'), cardType: CARD_TYPE, cardNo: CARD_NO }),
        { headers: headers(user), tags: { name: 'payment' } },
    );
    check(payRes, { 'payment status is 200': (r) => r.status === 200 });

    if (!counting) {
        return;
    }
    if (payRes.status !== 200) {
        paymentError.add(1);
        return;
    }
    const status = payRes.json('data.status');
    if (status === 'PENDING') {
        paymentPending.add(1);
    } else if (status === 'REQUESTED') {
        paymentRequested.add(1);
    } else if (status === 'FAILED') {
        paymentFailed.add(1);
    } else if (status === 'SUCCESS') {
        paymentSuccess.add(1);
    } else {
        paymentError.add(1);
    }
}

// P0: PG 를 직접 부른다. commerce-api 의 재시도 · 서킷이 끼면 시도 단위 실패율이 가려진다.
// orderId 는 6 자리 이상, callbackUrl 은 PG 가 http://localhost:8080 으로 시작하는 것만 받는다.
// 이 콜백은 commerce-api 에 대응하는 결제가 없어 ERROR 로그가 쌓이지만 정상이다.
export function pgDirect() {
    const orderId = String(100000 + exec.scenario.iterationInTest);
    const res = http.post(
        PG_URL + '/api/v1/payments',
        JSON.stringify({
            orderId: orderId,
            cardType: CARD_TYPE,
            cardNo: CARD_NO,
            amount: 10000,
            callbackUrl: BASE_URL + '/api/v1/payments/callback',
        }),
        {
            headers: { 'Content-Type': 'application/json', 'X-USER-ID': '1' },
            tags: { name: 'pg-direct' },
            responseType: 'none',
        },
    );
    if (res.status === 200) {
        pg200.add(1);
    } else if (res.status === 500) {
        pg500.add(1);
    } else {
        pgOther.add(1);
    }
}

// 가설 3 의 읽기 부하. 본문은 읽지 않으므로 버려 부하 생성기의 할당을 줄인다.
export function readOrder(data) {
    const idx = userIndex();
    const res = http.get(BASE_URL + '/api/v1/orders/' + data.orderIds[idx], {
        headers: headers(SEED_USERS[idx]),
        tags: { name: 'order-detail' },
        responseType: 'none',
    });
    check(res, { 'order-detail status is 200': (r) => r.status === 200 });
}

// ── 요약 ────────────────────────────────────────────────────────────────
function parseDurationSeconds(text) {
    // Go 스타일 기간 문자열("1m30s", "60s")을 초로 환산한다. 워밍업 유무 판단용.
    const re = /(\d+(?:\.\d+)?)(ms|s|m|h)/g;
    let total = 0;
    let match = re.exec(text);
    while (match !== null) {
        const value = parseFloat(match[1]);
        const unit = match[2];
        if (unit === 'ms') total += value / 1000;
        else if (unit === 's') total += value;
        else if (unit === 'm') total += value * 60;
        else if (unit === 'h') total += value * 3600;
        match = re.exec(text);
    }
    return total;
}

function metricCount(data, name) {
    const m = data.metrics[name];
    return m && m.values && typeof m.values.count === 'number' ? m.values.count : 0;
}

function fmtMs(value) {
    return typeof value === 'number' ? value.toFixed(1) + 'ms' : 'N/A';
}

function trendLine(data, label, key) {
    const m = data.metrics[key];
    if (!m || !m.values) {
        return ' ' + label + '  데이터 없음';
    }
    const v = m.values;
    return ' ' + label + '  p50=' + fmtMs(v.med) + '  p95=' + fmtMs(v['p(95)']) + '  p99=' + fmtMs(v['p(99)']) +
        '  max=' + fmtMs(v.max);
}

function buildConsoleSummary(data) {
    const lines = [];
    lines.push('================================================================');
    lines.push(' 결제 회복탄력성 측정 — MODE=' + MODE + ' / label=' + LABEL);
    lines.push('================================================================');

    if (MODE === 'pg-direct') {
        const c200 = metricCount(data, 'pg_200');
        const c500 = metricCount(data, 'pg_500');
        const other = metricCount(data, 'pg_other');
        const total = c200 + c500 + other;
        const ratio = total > 0 ? ((c500 / total) * 100).toFixed(1) : 'N/A';
        lines.push(' PG 직접 호출  200=' + c200 + '  500=' + c500 + '  기타=' + other + '  (합 ' + total + ')');
        lines.push(' pg_500 비율=' + ratio + '%  (판정: 37 ~ 43%)');
        lines.push(trendLine(data, 'pg-direct', durKey('pg-direct', 'direct')));
    } else {
        const pending = metricCount(data, 'payment_pending');
        const requested = metricCount(data, 'payment_requested');
        const failed = metricCount(data, 'payment_failed');
        const success = metricCount(data, 'payment_success');
        const error = metricCount(data, 'payment_error');
        const total = pending + requested + failed + success + error;
        if (MODE === 'mixed' && PAYMENT_TPS === 0) {
            lines.push(' 결제 없음 (PAYMENT_TPS=0, 가설 3 의 A)');
        } else {
            lines.push(' 결제 상태  PENDING=' + pending + '  REQUESTED=' + requested + '  FAILED=' + failed +
                '  SUCCESS=' + success + '  ERROR=' + error + '  (합 ' + total + ')');
            if (total > 0) {
                lines.push(' REQUESTED 비율=' + ((requested / total) * 100).toFixed(2) + '%');
            }
        }
        if (MODE === 'normal') {
            lines.push(trendLine(data, 'payment      ', durKey('payment', 'payment')));
        } else if (MODE === 'pg-down') {
            lines.push(trendLine(data, 'payment opening', durKey('payment', 'opening')));
            lines.push(trendLine(data, 'payment open   ', durKey('payment', 'open')));
        } else {
            if (PAYMENT_TPS > 0) {
                lines.push(trendLine(data, 'payment     ', durKey('payment', 'payment')));
            }
            lines.push(trendLine(data, 'order-detail', durKey('order-detail', 'read')));
        }
    }

    const dropped = metricCount(data, 'dropped_iterations');
    lines.push(' dropped_iterations=' + dropped +
        (dropped > 0 ? '  ← VU 가 부족하거나 서버가 도착률을 못 받았다. 이 측정은 판정하지 않는다.' : ''));

    Object.keys(data.metrics).forEach(function (name) {
        const th = data.metrics[name].thresholds;
        if (th) {
            Object.keys(th).forEach(function (expr) {
                lines.push(' threshold[' + name + ' ' + expr + ']: ' + (th[expr].ok ? 'PASS' : 'FAIL'));
            });
        }
    });
    lines.push('================================================================');
    return lines.join('\n') + '\n';
}

export function handleSummary(data) {
    const result = {};
    result['loadtest/results/payment-' + MODE + '-' + LABEL + '.json'] = JSON.stringify(data, null, 2);
    result.stdout = buildConsoleSummary(data);
    return result;
}
