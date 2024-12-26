package com.example;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends CassandraRepository<Employee, EmployeePrimaryKey>  {

    List<Employee> findByKeyLocationAndKeyDepartment(String location, String department);
}
