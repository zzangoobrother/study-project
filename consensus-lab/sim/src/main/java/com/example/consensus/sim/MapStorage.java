package com.example.consensus.sim;

import java.util.Map;
import java.util.TreeMap;

/** {@link Storage}의 기본 구현. 순회 순서가 고정되도록 TreeMap을 쓴다. */
final class MapStorage implements Storage {

    private final Map<String, Object> data = new TreeMap<>();

    @Override
    public void put(String key, Object value) {
        data.put(key, value);
    }

    @Override
    public Object get(String key) {
        return data.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return data.containsKey(key) ? (T) data.get(key) : defaultValue;
    }
}
