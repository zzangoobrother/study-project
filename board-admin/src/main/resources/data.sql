-- 테스트 계정
insert into user_account (user_id, user_password, role_types, nickname, email, memo, created_at, created_by, modified_at, modified_by) values
    ('choi', '{noop}asdf1234', 'ADMIN', 'choi', 'choi@mail.com', 'I am choi.', now(), 'choi', now(), 'choi'),
    ('choi1', '{noop}asdf1234', 'MANAGER', 'choi1', 'choi1@mail.com', 'I am choi1.', now(), 'choi1', now(), 'choi1'),
    ('choi2', '{noop}asdf1234', 'MANAGER,DEVELOPER', 'choi2', 'choi2@mail.com', 'I am choi2.', now(), 'choi2', now(), 'choi2'),
    ('choi3', '{noop}asdf1234', 'USER', 'choi3', 'choi3@mail.com', 'I am choi3.', now(), 'choi3', now(), 'choi3')
;
