#!/usr/bin/env bash
# 측정 직전 · 직후에 한 번씩 실행해 차이를 본다. (2026-10-04 상품 캐시 설계 8.1 장)
# Com_select 는 MySQL 이 처리한 SELECT 수, keyspace_hits / misses 는 replica 의 캐시 적중 · 미스 수다.
# 읽기가 replica 로 가므로(설계 6.3 장) 적중은 redis-readonly 에서 센다. MGET 은 키 하나당 한 번씩 센다.
# 저장소 루트에서 실행한다.
set -euo pipefail

COMPOSE=(docker compose -f docker/loadtest-compose.yml -f docker/loadtest-cache.override.yml)

com_select=$("${COMPOSE[@]}" exec -T mysql \
  mysql -uapplication -papplication -N -e "SHOW GLOBAL STATUS LIKE 'Com_select'" 2>/dev/null | awk '{print $2}')
stats=$("${COMPOSE[@]}" exec -T redis-readonly redis-cli INFO stats | tr -d '\r')
hits=$(echo "$stats" | awk -F: '/^keyspace_hits/{print $2}')
misses=$(echo "$stats" | awk -F: '/^keyspace_misses/{print $2}')

echo "com_select=${com_select} keyspace_hits=${hits} keyspace_misses=${misses}"
