package com.example;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table
public class Employee {

    @PrimaryKey
    public final EmployeePrimaryKey key;

    @Column
    public final String phoneNumber;

    public Employee(EmployeePrimaryKey key, String phoneNumber) {
        this.key = key;
        this.phoneNumber = phoneNumber;
    }
}
