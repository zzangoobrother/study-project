package com.example.service;

import com.example.domain.Todo;
import com.example.dto.PageRequestDTO;
import com.example.dto.PageResponseDTO;
import com.example.dto.TodoDTO;

public interface TodoService {

    TodoDTO get(Long tno);

    Long register(TodoDTO dto);

    void modify(TodoDTO dto);

    void remove(Long tno);

    PageResponseDTO<TodoDTO> getList(PageRequestDTO pageRequestDTO);

    default TodoDTO entityToDTO(Todo todo) {
        return TodoDTO.builder()
                .tno(todo.getTno())
                .title(todo.getTitle())
                .content(todo.getContent())
                .complete(todo.isComplete())
                .dueDate(todo.getDueDate())
                .build();
    }

    default Todo dtoToEntity(TodoDTO todoDTO) {
        return Todo.builder()
                .tno(todoDTO.tno())
                .title(todoDTO.title())
                .content(todoDTO.content())
                .complete(todoDTO.complete())
                .dueDate(todoDTO.dueDate())
                .build();
    }
}
