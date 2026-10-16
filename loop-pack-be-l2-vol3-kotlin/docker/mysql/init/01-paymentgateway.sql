-- pg-simulator 전용 스키마. commerce-api 의 loopers 스키마와 같은 이름의 payments 테이블을 만들기 때문에 분리한다.
-- (2026-10-13 결제 설계 2.2 장)
-- 이 스크립트는 MySQL 볼륨이 비어 있을 때만 실행된다. 기존 볼륨이면 root 로 한 번 수동 실행한다.
CREATE DATABASE IF NOT EXISTS paymentgateway CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
GRANT ALL PRIVILEGES ON paymentgateway.* TO 'application'@'%';
FLUSH PRIVILEGES;
