package com.example.domain.cart;

import com.example.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Setter
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "cart_items", schema = "ecommerce")
public class CartItem extends BaseEntity {

    private static final int INIT_QUANTITY = 1;
    private  static final long INIT_CART_ITEM_ID = 0L;
    private  static final int DEFAULT_ADD_QUANTITY = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long cartItemId;

    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity")
    private int quantity = 0;

    public CartItem(Long cartItemId, Long cartId, Long productId, int quantity) {
        this.cartItemId = cartItemId;
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public static CartItem of(Long cartId, Long productId) {
        CartItem cartItem = new CartItem(INIT_CART_ITEM_ID, cartId, productId, INIT_QUANTITY);
        return cartItem;
    }

    public void delete() {
        this.isDeleted = true;
    }
}
