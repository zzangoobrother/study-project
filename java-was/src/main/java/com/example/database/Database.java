package com.example.database;

import java.util.Collection;
import java.util.Optional;

public interface Database<T> {

    long save(T data);

    Optional<T> findById(long id);

    void delete(long id);

    void update(long id, T data);

    Collection<T> findAll();
}
