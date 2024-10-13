package com.example.hellospring;

import com.example.hellospring.order.Order;
import com.example.hellospring.order.OrderService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;

public class OrderClient {
    public static void main(String[] args) {
        BeanFactory beanFactory = new AnnotationConfigApplicationContext(OrderConfig.class);
        OrderService service = beanFactory.getBean(OrderService.class);

        Order order = service.createOrder("0100", BigDecimal.TEN);
        System.out.println(order);

//        try {
//            new TransactionTemplate(transactionManager).execute(status -> {
//                Order order = new Order("100", BigDecimal.TEN);
//                repository.save(order);
//
//                return null;
//            });
//        } catch (DataIntegrityViolationException e) {
//            System.out.println("주문번호 중복 발생");
//        }
    }
}
