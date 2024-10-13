package com.example.hellospring;

import com.example.hellospring.data.JdbcOrderRepository;
import com.example.hellospring.order.OrderRepository;
import com.example.hellospring.order.OrderService;
import com.example.hellospring.order.OrderServiceImpl;
import com.example.hellospring.order.OrderServiceTxProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@Import(DataConfig.class)
public class OrderConfig {

    @Bean
    public OrderRepository orderRepository(DataSource dataSource) {
        return new JdbcOrderRepository(dataSource);
    }

    @Bean
    public OrderService orderService(OrderRepository orderRepository, PlatformTransactionManager transactionManager) {
        return new OrderServiceTxProxy(new OrderServiceImpl(orderRepository), transactionManager);
    }
}
