// 상품 목록 좋아요순 조회의 읽기 부하 시나리오. (2026-09-16 설계 문서 5.5 장)
//
// ab.js 와 분리한 이유는 주문 측정의 재현성이 그 파일에 묶여 있기 때문이다.
// 읽기 시나리오를 얹으면 기존 결과와 비교할 수 없게 된다.
//
// 도착률을 하나만 쓴다. 이 측정의 목적은 상한 탐색이 아니라 "인덱스 효과가 API 계층까지
// 살아남는가" 의 확인이다.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const TARGET_TPS = Number(__ENV.TARGET_TPS || 300);
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
};

export function listProducts() {
    const url = BASE_URL + '/api/v1/products'
        + '?sort=likes_desc&brandId=' + BRAND_ID + '&page=0&size=20';

    const res = http.get(url, { tags: { name: 'GET /api/v1/products' } });

    if (res.status === 200) {
        status200.add(1);
    } else {
        statusOther.add(1);
    }

    check(res, { 'status is 200': (r) => r.status === 200 });
}

// handleSummary 를 정의하면 k6 의 기본 콘솔 요약이 대체된다. ab.js 가 buildConsoleSummary 로
// 자체 요약을 만들어 stdout 에 돌려주는 것과 같은 이유로 여기서도 최소 요약을 직접 만든다.
// 이것이 없으면 아래 Step 2 의 확인 항목(200 비율·dropped_iterations)을 화면에서 볼 수 없다.
//
// 옵셔널 체이닝(?.)을 쓰지 않는다 — goja 지원 여부를 확인하지 못했다는 ab.js 의 판단을 따른다.
function buildConsoleSummary(data) {
    const m = data.metrics;
    const dur = m.http_req_duration.values;
    const ok = (m.product_list_status_200 || { values: { count: 0 } }).values.count;
    const bad = (m.product_list_status_other || { values: { count: 0 } }).values.count;
    const dropped = (m.dropped_iterations || { values: { count: 0 } }).values.count;

    return [
        '',
        '  label         : ' + LABEL + ' @ ' + TARGET_TPS + ' TPS',
        '  http_req_dur  : p95 ' + dur['p(95)'].toFixed(1) + 'ms  med ' + dur.med.toFixed(1)
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
