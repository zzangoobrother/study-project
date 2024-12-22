package com.example.application.helper;

import java.util.HashMap;
import java.util.Map;

public class JsonDeserializer {

    public static Map<String, String> simpleConvertJsonToMap(String target) {
        Map<String, String> jsonMap = new HashMap<>();

        if (target == null) {
            throw new NullPointerException("Input string is null");
        }

        String jsonTarget = target.trim();
        if (jsonTarget.startsWith("{") && jsonTarget.endsWith("}")) {
            jsonTarget = jsonTarget.substring(1, jsonTarget.length() - 1);
        }

        String[] keyValuePairs = jsonTarget.split("(?<!\\\\),");

        for (String pair : keyValuePairs) {
            String[] entry = pair.split("(?<!\\\\):");
            if (entry.length == 2) {
                String key = entry[0].trim().replaceAll("^\"|\"$", "").replace("\\,", ",").replace("\\:", ":");
                String value = entry[1].trim().replaceAll("^\"|\"$", "").replace("\\,", ",").replace("\\:", ":");
                jsonMap.put(key, value);
            } else {
                throw new IllegalArgumentException("Invalid JSON format");
            }
        }

        return jsonMap;
    }
}
