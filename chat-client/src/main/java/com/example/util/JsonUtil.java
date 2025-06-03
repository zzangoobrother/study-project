package com.example.util;

import com.example.service.TerminalService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static TerminalService terminalService;

    public static void setTerminalService(TerminalService terminalService) {
        JsonUtil.terminalService = terminalService;
    }

    public static <T> Optional<T> fromJson(String json, Class<T> clazz) {
        try {
            return Optional.of(objectMapper.readValue(json, clazz));
        } catch (Exception ex) {
            terminalService.printSystemMessage("Failed JSON to Object : " + ex.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<String> toJson(Object object) {
        try {
            return Optional.of(objectMapper.writeValueAsString(object));
        } catch (Exception ex) {
            terminalService.printSystemMessage("Failed Object to JSON : " + ex.getMessage());
            return Optional.empty();
        }
    }
}
