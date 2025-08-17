package com.example.service;

import com.example.dto.LoginRequestDTO;
import com.example.dto.LoginResponseDTO;
import com.example.dto.UserRequestDTO;
import com.example.entity.User;
import com.example.repository.UserRepository;
import com.example.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public void signup(UserRequestDTO userRequestDTO) {
        if (userRepository.findByUsername(userRequestDTO.username()).isPresent()) {
            throw new RuntimeException("이미 존재하는 사용자 입니다.");
        }

        User user = User.builder()
                .username(userRequestDTO.username())
                .password(passwordEncoder.encode(userRequestDTO.password()))
                .role("ROLE_USER")
                .build();

        userRepository.save(user);
    }

    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        User user = userRepository.findByUsername(loginRequestDTO.username()).orElseThrow(
                () -> new RuntimeException("사용자를 찾을 수 없습니다.")
        );

        if (!passwordEncoder.matches(loginRequestDTO.password(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        return new LoginResponseDTO(accessToken, refreshToken);
    }
}
