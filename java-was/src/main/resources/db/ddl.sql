CREATE TABLE IF NOT EXISTS users (
                       user_id INT PRIMARY KEY AUTO_INCREMENT,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       nickname VARCHAR(100) NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS POSTS (
                       post_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                       user_id BIGINT NOT NULL,
                       content VARCHAR(255) NOT NULL,
                       image_path VARCHAR(255) NOT NULL
);


CREATE TABLE IF NOT EXISTS comments (
                          comment_id INT PRIMARY KEY AUTO_INCREMENT,
                          post_id INT NOT NULL,
                          user_id INT NOT NULL,
                          content TEXT NOT NULL,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


INSERT INTO users (username, password, email, nickname) VALUES
    ('d', 'd', 'd@n', 'd'),
    ('user1001', 'password', 'user1001@example.com', 'User 1001'),
    ('user1002', 'password', 'user1002@example.com', 'User 1002'),
    ('user1003', 'password', 'user1003@example.com', 'User 1003');

INSERT INTO posts (user_id, content, image_path) VALUES
    (1, 'asdf', '3479a814-0272-49d8-8699-e318f23cc902.png');

INSERT INTO comments (post_id, user_id, content, created_at) VALUES
     (1, 1, 'This is the first comment.', '2024-07-16 14:30:00'),
     (1, 2, 'This is the second comment.', '2024-07-16 14:31:00'),
     (1, 3, 'This is the third comment.', '2024-07-16 14:32:00');