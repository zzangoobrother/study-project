// 상품 목록 좋아요순 조회의 읽기 부하 시나리오. (2026-09-16 설계 문서 5.5 장)
//
// ab.js 와 분리한 이유는 주문 측정의 재현성이 그 파일에 묶여 있기 때문이다.
// 읽기 시나리오를 얹으면 기존 결과와 비교할 수 없게 된다.
//
// 도착률을 하나만 쓴다. 이 측정의 목적은 상한 탐색이 아니라 "인덱스 효과가 API 계층까지
// 살아남는가" 의 확인이다.

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 기본값이 30 인 이유 — 300 에서는 before(인덱스 없음)가 붕괴한다(p95 60,002ms, dropped 16,063).
// 개선 전 상태의 절벽이 50 과 60 TPS 사이에 있어, before·after 가 둘 다 dropped = 0 인
// 도착률에서만 비교가 성립한다. (2026-09-24 계획서 Task 5 실행 결과)
// -e 를 빠뜨려도 붕괴 구간으로 돌아가지 않도록 기본값을 비교 가능한 값에 둔다.
const TARGET_TPS = Number(__ENV.TARGET_TPS || 30);
const DURATION = __ENV.DURATION || '60s';
const WARMUP = __ENV.WARMUP || '10s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LABEL = __ENV.LABEL || 'run';
const BRAND_ID = __ENV.BRAND_ID || '1';

const status200 = new Counter('product_list_status_200');
const statusOther = new Counter('product_list_status_other');

const preAllocatedVUs = Math.max(100, TARGET_TPS * 3);

export const options = {
    scenarios: {
        warmup: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: WARMUP,
            preAllocatedVUs: preAllocatedVUs,
            maxVUs: preAllocatedVUs * 3,
            exec: 'listProducts',
            tags: { phase: 'warmup' },
        },
        measurement: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: DURATION,
            startTime: WARMUP,
            preAllocatedVUs: preAllocatedVUs,
            maxVUs: preAllocatedVUs * 3,
            exec: 'listProducts',
            tags: { phase: 'measurement' },
        },
    },
    thresholds: {
        // 판정용이 아니라 서브메트릭을 만들기 위한 임계값이다. k6 는 thresholds 에 선언된
        // metric{tag:value} 만 handleSummary 의 data.metrics 에 따로 넣어 준다. 이것이 없으면
        // http_req_duration 이 warmup 과 measurement 를 합친 분포라 p95 에 워밍업이 섞인다.
        // (2026-09-24 측정의 200 건수 2,100 = 30 TPS × 70 초가 그 흔적이다)
        'http_req_duration{phase:measurement}': ['max>=0'],
    },
};

export function listProducts() {
    const url = BASE_URL + '/api/v1/products'
        + '?sort=likes_desc&brandId=' + BRAND_ID + '&page=0&size=20';

    const res = http.get(url, { tags: { name: 'GET /api/v1/products' } });

    check(res, { 'status is 200': (r) => r.status === 200 });

    // 상태 카운터도 측정 구간만 센다. p95 와 같은 구간의 건수여야 요약의 숫자끼리 맞는다. (ab.js 와 같은 방식)
    if (exec.scenario.name === 'measurement') {
        if (res.status === 200) {
            status200.add(1);
        } else {
            statusOther.add(1);
        }
    }
}

// handleSummary 를 정의하면 k6 의 기본 콘솔 요약이 대체된다. ab.js 가 buildConsoleSummary 로
// 자체 요약을 만들어 stdout 에 돌려주는 것과 같은 이유로 여기서도 최소 요약을 직접 만든다.
// 이것이 없으면 아래 Step 2 의 확인 항목(200 비율·dropped_iterations)을 화면에서 볼 수 없다.
//
// 옵셔널 체이닝(?.)을 쓰지 않는다 — goja 지원 여부를 확인하지 못했다는 ab.js 의 판단을 따른다.
//
// dropped_iterations 는 워밍업까지 합친 값을 그대로 쓴다. 워밍업에서 떨어진 반복도 그 도착률이
// 포화 구간이라는 신호이므로, "before·after 둘 다 dropped = 0" 게이트를 더 엄격하게 만들 뿐이다.
function buildConsoleSummary(data) {
    const m = data.metrics;
    const dur = m['http_req_duration{phase:measurement}'].values;
    const ok = (m.product_list_status_200 || { values: { count: 0 } }).values.count;
    const bad = (m.product_list_status_other || { values: { count: 0 } }).values.count;
    const dropped = (m.dropped_iterations || { values: { count: 0 } }).values.count;

    return [
        '',
        '  label         : ' + LABEL + ' @ ' + TARGET_TPS + ' TPS',
        '  http_req_dur  : (measurement) p95 ' + dur['p(95)'].toFixed(1) + 'ms  med ' + dur.med.toFixed(1)
            + 'ms  max ' + dur.max.toFixed(1) + 'ms',
        '  status 200    : ' + ok,
        '  status other  : ' + bad,
        '  dropped_iters : ' + dropped,
        '',
    ].join('\n');
}

export function handleSummary(data) {
    const path = 'loadtest/results/products-' + LABEL + '-' + TARGET_TPS + '.json';
    const result = {};
    result[path] = JSON.stringify(data, null, 2);
    result.stdout = buildConsoleSummary(data);
    return result;
}
