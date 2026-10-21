#!/usr/bin/env bash
# Toxiproxy 의 PG 프록시를 다룬다. (2026-10-13 결제 설계 11.2 장)
# 프록시 이름 pg, 리슨 127.0.0.1:18082, 업스트림 localhost:8082(호스트의 pg-simulator). Toxiproxy 자체도 호스트에서 돈다
# (docker/loadtest-payment.override.yml 머리 주석 — 컨테이너 Toxiproxy 는 호스트로 나가는 구간이 멈췄다).
#
# 사용: loadtest/pg-proxy.sh create | latency <ms> | clear | down | up
#
# down 은 프록시의 리스너를 닫아 연결 거부를 만든다. PG 프로세스를 죽이면 프록시가 연결을 받은 뒤 끊어
# commerce-api 에는 읽기 실패(Unknown)로 보이므로, 실제 PG 다운(연결 거부)을 흉내 내려면 프록시를 꺼야 한다.
# Toxiproxy 는 재시작하면 프록시를 잊으므로 toxiproxy-server 를 띄울 때마다 create 한다.
set -euo pipefail

API=http://localhost:8474

case "${1:-}" in
  create)
    curl -s -o /dev/null -X DELETE "$API/proxies/pg" || true
    curl -sf -X POST "$API/proxies" \
      -d '{"name":"pg","listen":"127.0.0.1:18082","upstream":"localhost:8082","enabled":true}'
    echo
    ;;
  latency)
    : "${2:?지연(ms)를 지정한다}"
    curl -sf -X POST "$API/proxies/pg/toxics" \
      -d "{\"name\":\"pg_latency\",\"type\":\"latency\",\"stream\":\"downstream\",\"attributes\":{\"latency\":$2,\"jitter\":0}}"
    echo
    ;;
  clear)
    curl -s -o /dev/null -X DELETE "$API/proxies/pg/toxics/pg_latency" || true
    ;;
  down)
    curl -sf -X POST "$API/proxies/pg" -d '{"enabled":false}'
    echo
    ;;
  up)
    curl -sf -X POST "$API/proxies/pg" -d '{"enabled":true}'
    echo
    ;;
  *)
    echo "사용: $0 create | latency <ms> | clear | down | up" >&2
    exit 1
    ;;
esac
