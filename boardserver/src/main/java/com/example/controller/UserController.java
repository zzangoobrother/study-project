package com.example.controller;

import com.example.aop.LoginCheck;
import com.example.dto.*;
import com.example.dto.request.UserLoginRequest;
import com.example.dto.request.UserUpdatePasswordRequest;
import com.example.dto.response.LoginResponse;
import com.example.dto.response.UserInfoResponse;
import com.example.service.impl.UserServiceImpl;
import com.example.utils.SessionUtil;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/users")
@RestController
public class UserController {

    private final UserServiceImpl userService;

    public UserController(UserServiceImpl userService) {
        this.userService = userService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/sing-up")
    public void singup(@RequestBody UserDTO userDTO) {
        if (UserDTO.hasNullDataBeforeRegister(userDTO)) {
            throw new RuntimeException("회원가입 정보를 확인해주세요.");
        }
        userService.register(userDTO);
    }

    @PostMapping("/sign-in")
    public HttpStatus login(@RequestBody UserLoginRequest userLoginRequest, HttpSession session) {
        ResponseEntity<LoginResponse> responseEntity = null;
        String id = userLoginRequest.userId();
        String password = userLoginRequest.password();
        UserDTO userInfo = userService.login(id, password);

        if (userInfo == null) {
            return HttpStatus.NOT_FOUND;
        } else if (userInfo != null) {
            LoginResponse loginResponse = LoginResponse.success(userInfo);
            if (userInfo.status() == UserDTO.Status.ADMIN) {
                SessionUtil.setLoginAdminId(session, id);
            } else {
                SessionUtil.setLoginMemberId(session, id);
            }

            responseEntity = new ResponseEntity<>(loginResponse, HttpStatus.OK);
        } else {
            throw new RuntimeException("Login Error 유저 정보가 없거나 지원되지 않는 유저 입니다.");
        }

        return HttpStatus.OK;
    }

    @GetMapping("/my-info")
    public UserInfoResponse memberInfo(HttpSession session) {
        String id = SessionUtil.getLoginMemberId(session);
        if (id == null) {
            id = SessionUtil.getLoginAdminId(session);
        }

        UserDTO userDTO = userService.getUserInfo(id);
        return new UserInfoResponse(userDTO);
    }

    @PutMapping("/logout")
    public void logout(HttpSession session) {
        SessionUtil.clear(session);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @PatchMapping("/password")
    public ResponseEntity<LoginResponse> updateUserPassword(String accountId, @RequestBody UserUpdatePasswordRequest request, HttpSession session) {
        ResponseEntity<LoginResponse> responseEntity = null;
        String id = accountId;
        String beforePassword = request.beforePassword();
        String afterPassword = request.afterPassword();

        try {
            userService.updatePassword(id, beforePassword, afterPassword);
            UserDTO userInfo = userService.getUserInfo(id);
            responseEntity = ResponseEntity.ok(LoginResponse.success(userInfo));
        } catch (IllegalArgumentException e) {
            log.error("updatePassword 실패 {}", e);
            responseEntity = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return responseEntity;
    }

    @DeleteMapping
    public ResponseEntity<LoginResponse> deleteId(@RequestBody UserDeleteId userDeleteId, HttpSession session) {
        ResponseEntity<LoginResponse> responseEntity = null;
        String id = SessionUtil.getLoginMemberId(session);

        try {
            UserDTO userInfo = userService.getUserInfo(id);
            userService.deleteId(id, userDeleteId.password());
            responseEntity = ResponseEntity.ok(LoginResponse.success(userInfo));
        } catch (RuntimeException e) {
            log.error("deleteId 실패");
            responseEntity = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return responseEntity;
    }
}
