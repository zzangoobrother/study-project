package com.example.controller;

import com.example.dto.PageRequestDTO;
import com.example.dto.PageResponseDTO;
import com.example.dto.TodoDTO;
import com.example.service.TodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequiredArgsConstructor
@RequestMapping("/api/todo")
@RestController
public class TodoController {

    private final TodoService todoService;

    @GetMapping("/{tno}")
    public TodoDTO get(@PathVariable Long tno) {
        return todoService.get(tno);
    }

    @GetMapping("/list")
    public PageResponseDTO<TodoDTO> list(PageRequestDTO pageRequestDTO) {
        return todoService.getList(pageRequestDTO);
    }

    @PostMapping("/")
    public Map<String, Long> register(@RequestBody TodoDTO dto) {
        Long tno = todoService.register(dto);
        return Map.of("TNO", tno);
    }

    @PutMapping("/{tno}")
    public Map<String, String> modify(@PathVariable Long tno, @RequestBody TodoDTO todoDTO) {
        todoService.modify(todoDTO);
        return Map.of("RESULT", "SUCCESS");
    }

    @DeleteMapping("/{tno}")
    public Map<String, String> remove(@PathVariable Long tno) {
        todoService.remove(tno);
        return Map.of("RESULT", "SUCCESS");
    }
}
