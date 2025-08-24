package com.example.service;

import com.example.dto.PageRequestDTO;
import com.example.dto.TodoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

@SpringBootTest
class TodoServiceImplTest {

    @Autowired
    TodoService todoService;

    @Test
    void testGet() {
        Long tno = 1L;

        todoService.get(tno);
    }

    @Test
    void testRegister() {
        TodoDTO todoDTO = TodoDTO.builder()
                .title("aaa")
                .content("aaadd")
                .dueDate(LocalDate.of(2025, 8, 20))
                .build();

        todoService.register(todoDTO);
    }

    @Test
    void testGetList() {
        PageRequestDTO pageRequestDTO = PageRequestDTO.builder().build();
        todoService.getList(pageRequestDTO);
    }
}
