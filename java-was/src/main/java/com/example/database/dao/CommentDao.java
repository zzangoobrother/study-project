package com.example.database.dao;

import com.example.database.CommentVO;
import com.example.database.Database;

import java.util.List;

public interface CommentDao extends Database<CommentVO> {
    List<CommentVO> findByPostId(Long postId);
}
