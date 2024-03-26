package com.example.repository.cart;

import com.example.domain.cart.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByCustomerIdAndIsDeletedIsFalse(Long customerId);
}
