#!/usr/bin/env bash
# 결제 회복탄력성 측정용 지표 샘플러. (2026-10-13 결제 설계 11.3 장)
# 1 초마다 /actuator/prometheus 에서 판정 지표를 뽑아 CSV 한 줄로 남긴다. 종료 신호(kill)를 받을 때까지 돈다.
#
# 사용: loadtest/payment-metrics.sh <out.csv>
#
# 값을 못 얻으면 빈 칸으로 두고 계속 간다 — 측정 도중 한 번의 스크레이프 실패가 샘플러 전체를 죽이면
# 그 뒤 구간의 서킷 · 풀 상태를 영영 잃는다. 빈 열이 많으면 판정 전에 지표 이름부터 의심한다.
set -uo pipefail

OUT="${1:?출력 CSV 경로를 지정한다}"
URL="${PROM_URL:-http://localhost:8081/actuator/prometheus}"

echo "epoch_ms,request_state,query_state,request_not_permitted,hikari_active,hikari_pending,tomcat_busy" > "$OUT"

while true; do
  # 경과 시간이 아니라 시각을 남긴다. k6 요약과 시각으로 맞춰 봐야 하기 때문이다.
  # macOS 의 date 는 %N 을 지원하지 않아 python 대신 perl 로 밀리초를 얻는다.
  NOW=$(perl -MTime::HiRes=time -e 'printf("%d", time()*1000)')
  BODY=$(curl -s --max-time 2 "$URL" || true)

  # 서킷 상태는 상태마다 줄이 있고 현재 상태만 1.0 이다. 그 줄의 state 라벨이 값이다.
  state() {
    printf '%s\n' "$BODY" | grep "^resilience4j_circuitbreaker_state{" | grep "name=\"$1\"" | grep ' 1.0$' \
      | sed -E 's/.*state="([a-z_]+)".*/\1/' | head -1
  }
  # 라벨 순서에 기대지 않도록 패턴마다 grep 을 겹쳐 건다.
  value() {
    local line
    line=$(printf '%s\n' "$BODY" | grep "^$1{" | grep -E "$2" | head -1)
    printf '%s' "${line##* }"
  }

  RS=$(state pgRequest)
  QS=$(state pgQuery)
  NP=$(value resilience4j_circuitbreaker_not_permitted_calls_total 'name="pgRequest"')
  HA=$(value hikaricp_connections_active 'pool="mysql-main-pool"')
  HP=$(value hikaricp_connections_pending 'pool="mysql-main-pool"')
  TB=$(value tomcat_threads_busy_threads 'name="http-nio-8080"')

  echo "$NOW,$RS,$QS,$NP,$HA,$HP,$TB" >> "$OUT"
  sleep 1
done
