package com.example.exception.handler;

import com.example.dto.response.CommonResponse;
import com.example.exception.BoardServerException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

public class CustomExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException e) {
        CommonResponse<String> commonResponse = new CommonResponse(HttpStatus.OK, "RuntimeException", e.getMessage(), e.getMessage());
        return new ResponseEntity<>(commonResponse, new HttpHeaders(), commonResponse.status());
    }

    @ExceptionHandler(BoardServerException.class)
    public ResponseEntity<Object> handleBoardServerException(BoardServerException e) {
        CommonResponse<String> commonResponse = new CommonResponse(HttpStatus.OK, "BoardServerException", e.getMessage(), e.getMessage());
        return new ResponseEntity<>(commonResponse, new HttpHeaders(), commonResponse.status());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e) {
        CommonResponse<String> commonResponse = new CommonResponse(HttpStatus.OK, "Exception", e.getMessage(), e.getMessage());
        return new ResponseEntity<>(commonResponse, new HttpHeaders(), commonResponse.status());
    }
}
