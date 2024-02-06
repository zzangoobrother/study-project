package com.example.fastcampusmysql.domain;

import org.springframework.data.domain.Sort;

public class PageHelper {
    public static String orderBy(Sort sort) {
        if (sort.isEmpty()) {
            return "id desc";
        }

        var orders = sort.toList();
        var orderBys = orders.stream()
                .map(order -> order.getProperty() + " " + order.getDirection())
                .toList();

        return String.join(", ", orderBys);
    }
}
