insert into `users` (`id`, `email`, `nickname`, `address`, `certification_code`, `status`, `last_login_at`)
values (1, 'abc@naver.com', 'abc', 'Seoul', 'aaaaaaaa-aaaa-aaaa-aaaaaaaaaa', 'ACTIVE', 0);

insert into `posts` (`id`, `content`, `created_at`, `modified_at`, `user_id`)
values (1, 'helloword', 1678530673958, 1678530673958, 1);
