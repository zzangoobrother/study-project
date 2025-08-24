package com.example.service;

import com.example.domain.Todo;
import com.example.dto.PageRequestDTO;
import com.example.dto.PageResponseDTO;
import com.example.dto.TodoDTO;
import com.example.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class TodoServiceImpl implements TodoService {

    private final TodoRepository todoRepository;

    @Override
    public TodoDTO get(Long tno) {
        Optional<Todo> result = todoRepository.findById(tno);
        Todo todo = result.orElseThrow();
        return entityToDTO(todo);
    }

    @Override
    public Long register(TodoDTO dto) {
        Todo todo = dtoToEntity(dto);
        return todoRepository.save(todo).getTno();
    }

    @Transactional
    @Override
    public void modify(TodoDTO dto) {
        Optional<Todo> result = todoRepository.findById(dto.tno());
        Todo todo = result.orElseThrow();

        todo.changeTitle(dto.title());
        todo.changeContent(dto.content());
        todo.changeComplete(dto.complete());
        todo.changeDueDate(dto.dueDate());

        todoRepository.save(todo);
    }

    @Override
    public void remove(Long tno) {
        todoRepository.deleteById(tno);
    }

    @Override
    public PageResponseDTO<TodoDTO> getList(PageRequestDTO pageRequestDTO) {
        Page<Todo> result = todoRepository.search1(pageRequestDTO);
        List<TodoDTO> dtoList = result.map(this::entityToDTO).toList();

        return PageResponseDTO.<TodoDTO>withAll()
                .dtoList(dtoList)
                .pageRequestDTO(pageRequestDTO)
                .total(result.getTotalElements())
                .build();
    }
}
