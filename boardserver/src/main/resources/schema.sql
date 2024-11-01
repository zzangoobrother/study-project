CREATE TABLE USERS(
    ID INT PRIMARY KEY AUTO_INCREMENT,
    userId VARCHAR(50) NOT NULL                  COMMENT '회원 아이디',
    password VARCHAR(50) NOT NULL     COMMENT '비밀번호',
    nickName VARCHAR(50) NOT NULL     COMMENT '닉네임',
    createTime date NULL                       COMMENT '생성일자',
    status VARCHAR(50) NULL            COMMENT '상태',
    isWithDraw bit NULL            COMMENT '상태'
);
