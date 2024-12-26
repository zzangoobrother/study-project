package com.example;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CassandraService {

    private final EmployeeRepository repository;

    public CassandraService(EmployeeRepository repository) {
        this.repository = repository;
    }

    public void casTest() {
        Employee employee = new Employee(new EmployeePrimaryKey("seoul", "business", "key"), "010-1234-1234");

        repository.save(employee);

        Employee employee2 = new Employee(new EmployeePrimaryKey("seoul", "business", "joy"), "010-5678-5678");

        repository.save(employee2);

        List<Employee> result = repository.findByKeyLocationAndKeyDepartment("seoul", "business");
    }
}
