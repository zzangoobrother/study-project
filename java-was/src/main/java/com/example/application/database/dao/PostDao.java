package com.example.application.database.dao;

import com.example.application.database.Database;
import com.example.application.database.vo.PostListVO;
import com.example.application.database.vo.PostVO;

import java.util.List;

public interface PostDao extends Database<PostVO> {
    List<PostListVO> findAllJoinFetch();
}
