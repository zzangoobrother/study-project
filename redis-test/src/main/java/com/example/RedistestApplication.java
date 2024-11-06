package com.example;

import com.example.domain.entity.User;
import com.example.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@RequiredArgsConstructor
@SpringBootApplication
public class RedistestApplication implements ApplicationRunner {

    private final UserRepository userRepository;

    public static void main(String[] args) {
        SpringApplication.run(RedistestApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
//        userRepository.save(User.builder().name("greg1").email("greg1@gmail.com").build());
//        userRepository.save(User.builder().name("greg2").email("greg2@gmail.com").build());
//        userRepository.save(User.builder().name("greg3").email("greg3@gmail.com").build());
//        userRepository.save(User.builder().name("greg4").email("greg4@gmail.com").build());
    }
}
