create table coupons
(
    'id' bigint(20) not null auto_increment,
    'title' varchar(255) not null comment '쿠폰명',
    'coupon_type' varchar(255) not null comment '쿠폰 타입 (선착순 쿠폰, ...)',
    'total_quantity' int null comment '쿠폰 발급 최대 수량',
    'issued_quantity' int not null comment '발급된 쿠폰 수량',
    'discount_amount' int not null comment '할인금액',
    'min_available_amount' int not null comment '최소 사용 금액',
    'date_issue_start' datetime(6) not null comment '발급 시작 일시',
    'date_issue_end' datetime(6) not null comment '발급 종료 일시',
    'date_created' datetime(6) not null comment '생성 일시',
    'date_updated' datetime(6) not null comment '수정 일시',
    primary key ('id')
) engine = InnoDB
  default charset = utf8m64
    comment '쿠폰 정책';

create table 'coupon_issues'
(
    'id' bigint(20) not null auto_increment,
    'coupon_id' bigint(20) not null comment '쿠폰 ID',
    'user_id' bigint(20) not null comment '유저 ID',
    'date_issued' datetime(6) not null comment '발급 일시',
    'date_used' datetime(6) null comment '사용 일시',
    'date_created' datetime(6) not null comment '수정 일시',
    'date-updated' datetime(6) not null comment '수정 일시',
    primary key ('id')
) engine = InnoDB
  default charset = utf8m64
    comment '쿠폰 발급 내역';
