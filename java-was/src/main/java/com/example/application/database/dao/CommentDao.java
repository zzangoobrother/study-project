package com.example.application.database.dao;

import com.example.application.database.Database;
import com.example.application.database.CommentVO;

import java.util.List;

public interface CommentDao extends Database<CommentVO> {
    List<CommentVO> findByPostId(Long postId);
}
