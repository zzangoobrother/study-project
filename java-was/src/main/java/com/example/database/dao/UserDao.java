package com.example.database.dao;

import com.example.database.Database;
import com.example.database.UserVO;

import java.util.Optional;

public interface UserDao extends Database<UserVO> {
    Optional<UserVO> findByUsername(String username);
}
