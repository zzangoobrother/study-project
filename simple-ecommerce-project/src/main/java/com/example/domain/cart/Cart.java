package com.example.domain.cart;

import lombok.Getter;

import javax.persistence.*;

@Getter
@Entity
@Table(name = "carts", schema = "ecommerce")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "customer_id")
    private Long customerId;

    public static Cart of(Long customerId) {
        Cart cart = new Cart();
        cart.customerId = customerId;
        return cart;
    }
}
