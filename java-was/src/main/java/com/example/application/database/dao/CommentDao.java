package com.example.application.database.dao;

import com.example.application.database.Database;
import com.example.application.database.vo.CommentVO;
import com.example.application.database.vo.CommentListVO;

import java.util.List;

public interface CommentDao extends Database<CommentVO> {
    List<CommentVO> findByPostId(Long postId);

    List<CommentListVO> findCommentsJoinFetch(Long postId);
}
