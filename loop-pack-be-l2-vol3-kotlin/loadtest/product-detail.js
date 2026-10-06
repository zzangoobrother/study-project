// 상품 상세 조회의 읽기 부하 시나리오. 좋아요 쓰기를 섞을 수 있다. (2026-10-04 상품 캐시 설계 8 장)
//
// 요청의 80% 를 상위 100 개 상품에, 20% 를 전체 10 만 건에서 무작위로 보낸다. 10 만 건에 고르게 보내면
// 측정 시간 안에 같은 상품이 거의 다시 오지 않아 적중이 생기지 않는다. 이 분포는 가정이며 결과에 그렇게 적는다.
//
// WRITE_RATIO 는 좋아요(등록 · 취소 반반) 요청의 비율이다. 쓰기는 상위 100 개에만 보낸다 — 무효화가
// 적중률을 얼마나 깎는지 보려는 것이므로 캐시에 있을 법한 상품을 지워야 한다(H3).

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 기본값 30 은 products.js 와 같은 이유다 — before 가 무너지지 않는 도착률에서만 비교가 성립한다.
const TARGET_TPS = Number(__ENV.TARGET_TPS || 30);
const DURATION = __ENV.DURATION || '60s';
const WARMUP = __ENV.WARMUP || '10s';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LABEL = __ENV.LABEL || 'run';
const WRITE_RATIO = Number(__ENV.WRITE_RATIO || 0);

const PRODUCT_COUNT = 100000; // loadtest/seed-products.sql
const HOT_SET_SIZE = 100;
const HOT_RATIO = 0.8;
const USERS = ['seeduser01', 'seeduser02', 'seeduser03']; // LocalDataSeeder

const detail200 = new Counter('product_detail_status_200');
const detailOther = new Counter('product_detail_status_other');
const likeRequests = new Counter('product_like_requests');

const preAllocatedVUs = Math.max(100, TARGET_TPS * 3);

function scenario(name, extra) {
    const base = {
        executor: 'constant-arrival-rate',
        rate: TARGET_TPS,
        timeUnit: '1s',
        preAllocatedVUs: preAllocatedVUs,
        maxVUs: preAllocatedVUs * 3,
        exec: 'run',
        tags: { phase: name },
    };
    return Object.assign(base, extra);
}

export const options = {
    scenarios: {
        warmup: scenario('warmup', { duration: WARMUP }),
        measurement: scenario('measurement', { duration: DURATION, startTime: WARMUP }),
    },
    thresholds: {
        // 서브메트릭을 만들기 위한 임계값이다. 이유는 products.js 의 같은 줄 주석에 있다.
        'http_req_duration{phase:measurement,name:GET /api/v1/products/:id}': ['max>=0'],
    },
};

function randomInt(minInclusive, maxInclusive) {
    return minInclusive + Math.floor(Math.random() * (maxInclusive - minInclusive + 1));
}

function pickProductId() {
    return Math.random() < HOT_RATIO ? randomInt(1, HOT_SET_SIZE) : randomInt(1, PRODUCT_COUNT);
}

function toggleLike() {
    const productId = randomInt(1, HOT_SET_SIZE);
    const params = {
        headers: { 'X-Loopers-LoginId': USERS[randomInt(0, USERS.length - 1)] },
        tags: { name: 'like' },
    };
    const url = BASE_URL + '/api/v1/products/' + productId + '/likes';
    if (Math.random() < 0.5) {
        http.post(url, null, params);
    } else {
        http.del(url, null, params);
    }
    if (exec.scenario.name === 'measurement') {
        likeRequests.add(1);
    }
}

export function run() {
    if (Math.random() < WRITE_RATIO) {
        toggleLike();
        return;
    }

    const res = http.get(BASE_URL + '/api/v1/products/' + pickProductId(), {
        tags: { name: 'GET /api/v1/products/:id' },
    });
    check(res, { 'status is 200': (r) => r.status === 200 });

    if (exec.scenario.name === 'measurement') {
        if (res.status === 200) {
            detail200.add(1);
        } else {
            detailOther.add(1);
        }
    }
}

// 옵셔널 체이닝(?.)을 쓰지 않는 이유는 products.js 와 같다.
function buildConsoleSummary(data) {
    const m = data.metrics;
    const dur = m['http_req_duration{phase:measurement,name:GET /api/v1/products/:id}'].values;
    const ok = (m.product_detail_status_200 || { values: { count: 0 } }).values.count;
    const bad = (m.product_detail_status_other || { values: { count: 0 } }).values.count;
    const likes = (m.product_like_requests || { values: { count: 0 } }).values.count;
    const dropped = (m.dropped_iterations || { values: { count: 0 } }).values.count;

    return [
        '',
        '  label         : ' + LABEL + ' @ ' + TARGET_TPS + ' TPS, write ' + WRITE_RATIO,
        '  detail dur    : (measurement) p95 ' + dur['p(95)'].toFixed(1) + 'ms  med ' + dur.med.toFixed(1)
            + 'ms  max ' + dur.max.toFixed(1) + 'ms',
        '  status 200    : ' + ok,
        '  status other  : ' + bad,
        '  like requests : ' + likes,
        '  dropped_iters : ' + dropped,
        '',
    ].join('\n');
}

export function handleSummary(data) {
    const path = 'loadtest/results/product-detail-' + LABEL + '-' + TARGET_TPS + '-w' + WRITE_RATIO + '.json';
    const result = {};
    result[path] = JSON.stringify(data, null, 2);
    result.stdout = buildConsoleSummary(data);
    return result;
}
