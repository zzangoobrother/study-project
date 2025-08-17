package com.example.controller;

import com.example.dto.LoginRequestDTO;
import com.example.dto.LoginResponseDTO;
import com.example.dto.UserRequestDTO;
import com.example.entity.User;
import com.example.repository.UserRepository;
import com.example.service.UserService;
import com.example.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/auth")
@RequiredArgsConstructor
@RestController
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public String signup(@RequestBody UserRequestDTO userRequestDTO) {
        userService.signup(userRequestDTO);
        return "회원가입 성공";
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO loginRequestDTO) {
        return userService.login(loginRequestDTO);
    }

    @PostMapping("/refresh")
    public LoginResponseDTO refresh(@RequestHeader("Authorization") String refreshToken) {
        String token = refreshToken.replace("Bearer ", "");
        System.out.println(token);

        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Refresh Token이 유효하지 않습니다.");
        }

        String username = jwtTokenProvider.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("사용자가 존재하지 않습니다."));

        if (!token.equals(user.getRefreshToken())) {
            throw new RuntimeException("서버에 저장된 Refresh Token과 일지하지 않습니다.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(username);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

        user.setRefreshToken(newRefreshToken);
        userRepository.save(user);

        return new LoginResponseDTO(accessToken, refreshToken);
    }

    @PostMapping("/logout")
    public String logout(@RequestHeader("Authorization") String accessToken) {
        String token = accessToken.replace("Bearer ", "");
        String username = jwtTokenProvider.getUsernameFromToken(token);
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("사용자가 존재하지 않습니다."));

        user.setRefreshToken(null);
        userRepository.save(user);

        return "로그아웃 성공";
    }
}
