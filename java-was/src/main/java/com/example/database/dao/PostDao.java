package com.example.database.dao;

import com.example.database.Database;
import com.example.database.PostListVO;
import com.example.database.PostVO;

import java.util.List;

public interface PostDao extends Database<PostVO> {
    List<PostListVO> findAllJoinFetch();
}
