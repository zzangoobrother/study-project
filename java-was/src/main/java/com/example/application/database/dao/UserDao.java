package com.example.application.database.dao;

import com.example.application.database.Database;
import com.example.application.database.vo.UserVO;

import java.util.Optional;

public interface UserDao extends Database<UserVO> {
    Optional<UserVO> findByUsername(String username);
}
