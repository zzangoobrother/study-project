// 주문 API 처리량 A/B 부하 테스트 스크립트.
//
// k6 v2.1.0 대상. k6 코어 모듈만 쓴다(k6/http, k6/metrics, k6/execution, k6) — jslib.k6.io 같은
// 외부 CDN import 를 쓰지 않는다. 그래서 handleSummary 의 콘솔 출력도 k6 기본 리포트 형태를
// 그대로 재현하는 대신, 이 스크립트가 자체적으로 만든 요약(buildConsoleSummary)을 쓴다.
//
// 설계 문서: docs/superpowers/specs/2026-09-06-order-throughput-design.md
//
// [불확실한 부분 — 실행해서 검증하지 못했다]
// - `k6/execution`, tags 를 통한 threshold 서브메트릭 생성(`metric{tag:value}` 키로 data.metrics 에
//   노출되는 것), Trend 요약값(avg/med/p(90)/p(95)/max)의 필드 구성은 k6 v0.4x ~ v1.x 기준 동작이다.
//   v2.1.0 에서 API 가 그대로인지 확인하지 못했다.
// - goja(k6 의 JS 엔진)가 옵셔널 체이닝(?.)을 지원하는지 확신이 없어 이 스크립트는 의도적으로 쓰지 않았다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

// ── 환경변수 ────────────────────────────────────────────────────────────
const SCENARIO_NAME = __ENV.SCENARIO || 'hotspot'; // 'hotspot' | 'spread'
const TARGET_TPS = Number(__ENV.TARGET_TPS || 300);
const DURATION = __ENV.DURATION || '60s';
const WARMUP = __ENV.WARMUP || '10s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LABEL = __ENV.LABEL || 'run'; // 결과 파일명 라벨. before/after 등으로 지정해 구분한다.
// p95 임계값(ms). 설계 문서가 구체적 SLA 를 정하지 않아 임의의 기본값을 둔다 — 시나리오/TARGET_TPS 에
// 맞춰 실행할 때마다 조정해서 쓰라는 뜻이다. (설계 문서 12.3 장 — 반드시 응답시간 기준이 있어야 한다)
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 200);

// LocalDataSeeder 가 넣는 시드 데이터 (apps/commerce-api/.../support/seed/LocalDataSeeder.kt)
const SEED_USERS = ['seeduser01', 'seeduser02', 'seeduser03'];
const PRODUCT_COUNT = 137;

// ── VU 풀 크기 ──────────────────────────────────────────────────────────
// 락 대기로 응답이 늦어져도(설계 문서 12.3 장 — 핫스팟 800 TPS 에서 p95 2.48초 관측) 도착률을 유지하려면
// VU 가 충분히 있어야 한다. 부족하면 k6 가 반복을 건너뛰어(dropped_iterations) 실제 도착률이
// TARGET_TPS 에 못 미치고, 그러면 "서버 한계인지 VU 부족인지" 구분이 안 돼 측정이 무의미해진다.
// MAX_EXPECTED_LATENCY_SEC 는 근거 있는 값이 아니라 넉넉히 잡은 여유값이다 — 요약에 찍히는
// dropped_iterations 가 0 이 아니면 이 값을 올려 재측정해야 한다.
// preAllocatedVUs 를 TARGET_TPS 의 2배로 잡는다. 1배로 두고 재보니 핫스팟 600 TPS 에서
// dropped_iterations 가 326 건 나왔다 — maxVUs 여유는 충분했는데도 그랬다. k6 는 preAllocated 를
// 넘어서면 VU 런타임을 그 자리에서 새로 만들어야 하고, 그 초기화가 도착률을 따라가지 못한다.
// 미리 만들어 두는 편이 측정 중 흔들림이 없다.
const MAX_EXPECTED_LATENCY_SEC = 10;
const preAllocatedVUs = Math.max(100, TARGET_TPS * 2);
const maxVUs = Math.max(preAllocatedVUs, TARGET_TPS * MAX_EXPECTED_LATENCY_SEC);

// ── 커스텀 메트릭 ───────────────────────────────────────────────────────
// 재고 소진(409)이 섞이면 그 측정은 무효다(설계 문서 3.3 장). 사후에 판별할 수 있도록 상태코드
// 분포를 별도로 센다. warmup 구간은 세지 않는다 — 합치면 "측정 구간에 409 가 섞였는가"라는
// 원래 질문(재고 소진 여부)이 흐려진다.
const status200 = new Counter('order_status_200');
const status409 = new Counter('order_status_409');
const statusOther = new Counter('order_status_other');

export const options = {
    scenarios: {
        // 워밍업: 지표에서 제외되지만 서버 JIT/커넥션 풀을 목표 도착률로 미리 데운다.
        // "별도 시나리오 + phase 태그" 둘 다로 측정 구간과 분리한다(팀 지시).
        warmup: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: WARMUP,
            preAllocatedVUs: preAllocatedVUs,
            maxVUs: maxVUs,
            exec: 'placeOrder',
            startTime: '0s',
            tags: { phase: 'warmup' },
        },
        // 측정: warmup 이 끝난 뒤 이어서 시작한다.
        measurement: {
            executor: 'constant-arrival-rate', // VU 고정(constant-vus)이 아니다. 서버가 느려지면
            // 부하도 같이 줄어들어 "얼마나 들어왔을 때 무너지는가"가 안 보인다(설계 문서 3.2 장).
            // 도착률을 고정해야 한계가 드러난다.
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: preAllocatedVUs,
            maxVUs: maxVUs,
            exec: 'placeOrder',
            startTime: WARMUP,
            tags: { phase: 'measurement' },
        },
    },
    thresholds: {
        // 설계 문서 12.3 장의 교훈: 락 대기는 커넥션을 쥔 채 일어나 connection-timeout(3s)에 걸리지 않고
        // innodb_lock_wait_timeout(50s)도 여유로워 서버 쪽 에러율은 0%로 보인다. "에러율 0%, 정상"인데
        // 실제 클라이언트는 타임아웃으로 전부 실패하는 상황이 이래서 생긴다. 그래서 에러율이 아니라
        // 응답 시간 자체에 임계값을 건다. {phase:measurement} 태그로 warmup 구간을 계산에서 뺀다.
        [`http_req_duration{phase:measurement}`]: [`p(95)<${P95_THRESHOLD_MS}`],
    },
    // abortOnFail 을 쓰지 않는다(어느 threshold 에도). 임계값을 넘겨도 끝까지 돌려야 "얼마나 넘었는지"를
    // 알 수 있다 — 중간에 멈추면 한계를 넘었다는 사실 자체는 이미 관측된 것이라 강제 종료가 얻는 게 없다.

    // 응답 본문을 버린다. 이 스크립트는 status 만 보고 본문을 읽지 않는데, 기본값(false)이면 k6 가
    // 응답마다 JS 문자열을 할당한다. k6 는 측정 대상과 같은 머신(호스트)에서 도는 만큼 부하 도구가
    // 쓰는 CPU/메모리를 줄여야 한다 — 부하 생성기가 먼저 포화되면 재는 것이 서버 한계가 아니게 된다.
    discardResponseBodies: true,
};

export function placeOrder() {
    const phase = exec.scenario.name; // 'warmup' | 'measurement'

    // 회원 라운드로빈: VU 마다 고정된 회원을 쓴다. VU 수가 3의 배수가 아니어도 VU 가 많으므로
    // 테스트 전체로 보면 세 회원에 고르게 분산된다.
    const user = SEED_USERS[exec.vu.idInInstance % SEED_USERS.length];

    // hotspot: 항상 productId=1 (단일 행 배타 락 직렬화를 본다)
    // spread : 137개 중 랜덤 (커넥션 풀 고갈을 본다) — 설계 문서 3.4 장
    const productId = SCENARIO_NAME === 'spread' ? Math.floor(Math.random() * PRODUCT_COUNT) + 1 : 1;

    // couponId 는 보내지 않는다. user_coupons 는 1인 1매·1회용이라 두 번째 요청부터 409 가 나
    // 지속 부하를 걸 수 없다(설계 문서 12.2 장). 쿠폰 경로는 이 스크립트의 측정 대상이 아니다.
    const payload = JSON.stringify({
        items: [{ productId: productId, quantity: 1 }],
    });

    const res = http.post(BASE_URL + '/api/v1/orders', payload, {
        headers: {
            'Content-Type': 'application/json',
            'X-Loopers-LoginId': user,
        },
        tags: { phase: phase },
    });

    check(res, {
        'status is 200 or 409': (r) => r.status === 200 || r.status === 409,
    });

    if (phase === 'measurement') {
        if (res.status === 200) {
            status200.add(1);
        } else if (res.status === 409) {
            status409.add(1);
        } else {
            statusOther.add(1);
        }
    }
}

// ── 요약 ────────────────────────────────────────────────────────────────
function parseDurationSeconds(text) {
    // Go 스타일 기간 문자열("1m30s", "60s" 등)을 초 단위로 환산한다. TARGET_TPS 달성률 계산용.
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
    return total > 0 ? total : NaN;
}

function metricCount(data, name) {
    const m = data.metrics[name];
    return m && m.values && typeof m.values.count === 'number' ? m.values.count : 0;
}

function pct(part, total) {
    return total > 0 ? ((part / total) * 100).toFixed(1) : '0.0';
}

function fmtMs(value) {
    return typeof value === 'number' ? value.toFixed(1) + 'ms' : 'N/A';
}

function buildConsoleSummary(data) {
    const durKey = 'http_req_duration{phase:measurement}';
    const durValues = data.metrics[durKey] && data.metrics[durKey].values ? data.metrics[durKey].values : null;
    const thresholds = data.metrics[durKey] && data.metrics[durKey].thresholds ? data.metrics[durKey].thresholds : null;

    const c200 = metricCount(data, 'order_status_200');
    const c409 = metricCount(data, 'order_status_409');
    const cOther = metricCount(data, 'order_status_other');
    const total = c200 + c409 + cOther;

    const durationSec = parseDurationSeconds(DURATION);
    const achievedTps = !isNaN(durationSec) && durationSec > 0 ? (total / durationSec).toFixed(1) : 'N/A';

    const dropped = metricCount(data, 'dropped_iterations');

    const lines = [];
    lines.push('================================================================');
    lines.push(' 주문 처리량 A/B 부하 테스트 — ' + SCENARIO_NAME + ' / label=' + LABEL + ' / target=' + TARGET_TPS + ' TPS');
    lines.push('================================================================');
    lines.push(' 측정 구간(warmup ' + WARMUP + ' 제외, duration=' + DURATION + '): 요청 수=' + total + ', 달성 TPS=' + achievedTps);
    if (durValues !== null) {
        lines.push(
            ' http_req_duration(measurement)  avg=' + fmtMs(durValues.avg) +
            '  med=' + fmtMs(durValues.med) +
            '  p90=' + fmtMs(durValues['p(90)']) +
            '  p95=' + fmtMs(durValues['p(95)']) +
            '  max=' + fmtMs(durValues.max),
        );
    } else {
        lines.push(' http_req_duration(measurement)  데이터 없음 — threshold 서브메트릭이 생성되지 않았다.');
    }
    lines.push(
        ' 상태코드  200=' + c200 + ' (' + pct(c200, total) + '%)' +
        '  409=' + c409 + ' (' + pct(c409, total) + '%)' +
        '  기타=' + cOther + ' (' + pct(cOther, total) + '%)',
    );
    lines.push(' dropped_iterations=' + dropped + (dropped > 0 ? '  ← VU 가 부족해 목표 TPS 를 못 채웠다. ab.js 의 MAX_EXPECTED_LATENCY_SEC 를 올려라.' : ''));
    if (thresholds !== null) {
        Object.keys(thresholds).forEach(function (expr) {
            lines.push(' threshold[' + expr + ']: ' + (thresholds[expr].ok ? 'PASS' : 'FAIL'));
        });
    }
    if (c409 > 0) {
        lines.push('----------------------------------------------------------------');
        lines.push(' ⚠ 409 가 섞여 있다 = 측정 중 재고가 소진됐다. 이 측정은 무효다.');
        lines.push('   loadtest/prepare.sql 을 다시 실행하고(재고 재주입) 재측정하라.');
    }
    lines.push('================================================================');
    return lines.join('\n') + '\n';
}

export function handleSummary(data) {
    const filename = 'loadtest/results/' + SCENARIO_NAME + '-' + LABEL + '-' + TARGET_TPS + '.json';
    const result = {};
    result[filename] = JSON.stringify(data, null, 2);
    result.stdout = buildConsoleSummary(data);
    return result;
}
