#!/usr/bin/env bash
# Toxiproxy 의 PG 프록시를 다룬다. (2026-10-13 결제 설계 11.2 장)
# 프록시 이름 pg, 리슨 0.0.0.0:18082(toxiproxy 컨테이너 안), 업스트림 commerce-api:8082.
# pg-simulator 는 commerce-api 의 네트워크 네임스페이스를 공유하므로 PG 가 듣는 8082 는 commerce-api:8082 로 보인다.
# Toxiproxy 와 PG 가 모두 컨테이너라 컨테이너 → 호스트 구간이 없다(docker/loadtest-payment.override.yml 머리 주석).
# 관리 API(8474)만 호스트에 열려 있어 이 스크립트가 호스트에서 부른다.
#
# 사용: loadtest/pg-proxy.sh create | latency <ms> | clear | down | up
#
# down 은 프록시의 리스너를 닫아 연결 거부를 만든다. PG 프로세스를 죽이면 프록시가 연결을 받은 뒤 끊어
# commerce-api 에는 읽기 실패(Unknown)로 보이므로, 실제 PG 다운(연결 거부)을 흉내 내려면 프록시를 꺼야 한다.
# Toxiproxy 는 재시작하면 프록시를 잊으므로 toxiproxy 컨테이너가 다시 만들어질 때마다 create 한다.
set -euo pipefail

API=http://localhost:8474

case "${1:-}" in
  create)
    curl -s -o /dev/null -X DELETE "$API/proxies/pg" || true
    curl -sf -X POST "$API/proxies" \
      -d '{"name":"pg","listen":"0.0.0.0:18082","upstream":"commerce-api:8082","enabled":true}'
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
