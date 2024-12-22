package com.example.application.database.dao;

import com.example.application.database.Database;
import com.example.application.database.PostListVO;
import com.example.application.database.PostVO;

import java.util.List;

public interface PostDao extends Database<PostVO> {
    List<PostListVO> findAllJoinFetch();
}
