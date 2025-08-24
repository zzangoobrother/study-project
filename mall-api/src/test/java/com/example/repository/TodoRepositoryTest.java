package com.example.repository;

import com.example.domain.Todo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TodoRepositoryTest {

    @Autowired
    private TodoRepository todoRepository;

    @Test
    void test1() {
        assertNotNull(todoRepository);
    }

    @Test
    void testInsert() {
        final Todo todo = Todo.builder()
                .title("title")
                .content("content")
                .dueDate(LocalDate.of(2025, 8, 24))
                .build();

        Todo result = todoRepository.save(todo);
    }

    @Test
    void testRead() {
        Long tno = 1L;

        Optional<Todo> result = todoRepository.findById(tno);


    }

    @Test
    void testUpdate() {
        Long tno = 1L;

        Optional<Todo> result = todoRepository.findById(tno);

        Todo todo = result.orElseThrow();


        todo.changeTitle("Update Title");
        todo.changeContent("Update Title");
        todo.changeComplete(true);

        todoRepository.save(todo);
    }

    @Test
    void testPaging() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("tno").descending());
        Page<Todo> result = todoRepository.findAll(pageable);

    }

//    @Test
//    void testSearch1() {
//        todoRepository.search1();
//    }
}
