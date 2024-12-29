package com.example.cassandra.repository;

import com.example.cassandra.entity.Product;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface ProductRepository extends CassandraRepository<Product, Long> {
}
