package com.example.application.database;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public abstract class InMemoryDatabaseImpl<T> implements Database<T> {

    private final Map<Long, T> database = new ConcurrentHashMap<>();
    private AtomicLong id = new AtomicLong(0);

    @Override
    public long save(T data) {
        validateValue(data);
        long key = id.incrementAndGet();
        database.put(key, data);
        return key;
    }

    @Override
    public Optional<T> findById(long id) {
        if(!database.containsKey(id)) {
            return Optional.empty();
        }
        return Optional.of(database.get(id));
    }

    @Override
    public void delete(long id) {
        validateId(id);
        database.remove(id);
    }

    @Override
    public void update(long id, T data) {
        validateId(id);
        validateValue(data);
        database.put(id, data);
    }

    private void validateValue(T data) {
        if (data == null) {
            throw new IllegalArgumentException("데이터가 null입니다.");
        }
    }

    private void validateId(long id) {
        if (!database.containsKey(id)) {
            throw new IllegalArgumentException("해당 id를 가진 데이터가 없습니다.");
        }
    }

    @Override
    public Collection<T> findAll() {
        return database.values();
    }

    public Collection<T> findAllByCondition(Predicate<T> predicate) {
        return database.values().stream()
                .filter(predicate)
                .toList();
    }

    public Optional<T> findByCondition(Predicate<T> predicate) {
        List<T> foundData = database.values().stream()
                .filter(predicate)
                .toList();

        if (foundData.size() > 1) {
            throw new IllegalArgumentException("조건에 맞는 데이터가 여러 개 존재합니다.");
        }

        return foundData.stream()
                .findFirst();
    }
}
