package com.example.service.impl;

import com.example.dto.UserDTO;
import com.example.exception.DuplicateIdException;
import com.example.mapper.UserProfileMapper;
import com.example.service.UserService;
import com.example.utils.SHA256Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    private final UserProfileMapper userProfileMapper;

    public UserServiceImpl(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public void register(UserDTO userProfile) {
        boolean dupleIdResult = isDuplicatedId(userProfile.userId());
        if (dupleIdResult) {
            throw new DuplicateIdException("중복된 아이디 입니다.");
        }

        userProfile.setCreateTime(new Date());
        userProfile.setPassword(SHA256Util.encryptSHA256(userProfile.password()));
        int insertCount = userProfileMapper.register(userProfile);

        if (insertCount != 1) {
            log.error("insertCount ERROR {}", userProfile);
            throw new RuntimeException("insertCount ERROR 회원가입 메서드를 확인해주세요. Params : " + userProfile);
        }
    }

    @Override
    public UserDTO login(String id, String password) {
        String cryptoPassword = SHA256Util.encryptSHA256(password);
        return userProfileMapper.findByIdAndPassword(id, cryptoPassword);
    }

    @Override
    public boolean isDuplicatedId(String id) {
        return userProfileMapper.idCheck(id) == 1;
    }

    @Override
    public UserDTO getUserInfo(String userId) {
        return null;
    }

    @Override
    public void updatePassword(String id, String beforePassword, String afterPassword) {
        String cryptoPassword = SHA256Util.encryptSHA256(beforePassword);
        UserDTO memberInfo = userProfileMapper.findByIdAndPassword(id, cryptoPassword);

        if (memberInfo != null) {
            memberInfo.setPassword(SHA256Util.encryptSHA256(afterPassword));
            int updateCount = userProfileMapper.updatePassword(memberInfo);
        } else {
            log.error("updatePassword ERROR {}", memberInfo);
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }
    }

    @Override
    public void deleteId(String id, String password) {
        String cryptoPassword = SHA256Util.encryptSHA256(password);
        UserDTO memberInfo = userProfileMapper.findByIdAndPassword(id, cryptoPassword);

        if (memberInfo != null) {
            int deleteCount = userProfileMapper.deleteUserProfile(id);
        } else {
            log.error("deleteId ERROR {}", memberInfo);
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }
    }
}
