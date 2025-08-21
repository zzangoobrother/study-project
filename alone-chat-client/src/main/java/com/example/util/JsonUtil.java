package com.example.util;

import com.example.service.TerminalService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

public class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static TerminalService terminalService;

    public static void setTerminalService(TerminalService terminalService) {
        JsonUtil.terminalService = terminalService;
    }

    public static <T> Optional<T> fromJson(String json, Class<T> clazz) {
        try {
            return Optional.of(mapper.readValue(json, clazz));
        } catch (Exception ex) {
            terminalService.printSystemMessage("Fail Json to Object : " + ex.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<String> toJson(Object obj) {
        try {
            return Optional.of(mapper.writeValueAsString(obj));
        } catch (Exception ex) {
            terminalService.printSystemMessage("Fail Object to Json : " + ex.getMessage());
            return Optional.empty();
        }
    }
}
