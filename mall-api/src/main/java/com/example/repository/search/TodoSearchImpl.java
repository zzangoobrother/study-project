package com.example.repository.search;

import com.example.domain.QTodo;
import com.example.domain.Todo;
import com.example.dto.PageRequestDTO;
import com.querydsl.jpa.JPQLQuery;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;

import java.util.List;

public class TodoSearchImpl extends QuerydslRepositorySupport implements TodoSearch {

    public TodoSearchImpl() {
        super(Todo.class);
    }

    @Override
    public Page<Todo> search1(PageRequestDTO pageRequestDTO) {
        QTodo todo = QTodo.todo;

        JPQLQuery<Todo> query = from(todo);

        query.where(todo.title.contains("1"));

        Pageable pageable = PageRequest.of(pageRequestDTO.getPage(), pageRequestDTO.getSize(), Sort.by("tno").descending());

        this.getQuerydsl().applyPagination(pageable, query);

        List<Todo> list =  query.fetch();
        long total = query.fetchCount();

        return new PageImpl<>(list, pageable, total);
    }
}
