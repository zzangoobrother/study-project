package com.example.controller.checkout;

import com.example.controller.dto.customer.CustomerDTO;
import com.example.controller.dto.product.ProductDTO;
import com.example.domain.cart.Cart;
import com.example.domain.cart.CartItemProduct;
import com.example.domain.customer.Customer;
import com.example.domain.customer.CustomerDetail;
import com.example.domain.product.Product;
import com.example.exception.NotFoundCartException;
import com.example.exception.NotFoundCartItemException;
import com.example.exception.NotFoundCustomerException;
import com.example.exception.NotFoundProductException;
import com.example.service.cart.CartService;
import com.example.service.product.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Controller
public class CheckoutController {

    private static final String ATTRIBUTE_NAME_CART_ID = "cartId";

    private final CartService cartService;
    private final ProductService productService;

    public CheckoutController(CartService cartService, ProductService productService) {
        this.cartService = cartService;
        this.productService = productService;
    }

    @GetMapping(value = "/checkout/direct-checkout")
    public String directCheckout(@AuthenticationPrincipal CustomerDetail customerDetail, @RequestParam("productId") Long productId, Model model) {
        // Direct Purchase
        log.info(">>> Handle payment for customer, {}", customerDetail);
        if (Objects.isNull(customerDetail)) {
            throw new NotFoundCustomerException("고객 정보를 찾을 수 없습니다.");
        }
        Customer customer = customerDetail.getCustomer();

        Optional<Product> optionalProduct = this.productService.findById(productId);
        if (optionalProduct.isEmpty()) {
            throw new NotFoundProductException("해당 상품 정보가 없습니다.");
        }
        Product product = optionalProduct.get();

        model.addAttribute("product", ProductDTO.of(product));
        model.addAttribute("customer", CustomerDTO.of(customer));
        model.addAttribute("totalAmount", product.getPrice());

        return "/checkout/direct-checkout";
    }

    @GetMapping(value = "/checkout/order-checkout")
    public String orderCheckout(@AuthenticationPrincipal CustomerDetail customerDetail, @RequestParam("cartId") Long cartId, Model model) {
        // Cart Purchase
        log.info(">>> Handle payment for customer, {}", customerDetail);
        if (Objects.isNull(customerDetail)) {
            throw new NotFoundCustomerException("Not found customer info");
        }

        Customer customer = customerDetail.getCustomer();
        cartService.getCartById(cartId);

        List<CartItemProduct> cartItemProducts = cartService.getCartItems(customerDetail.getCustomer().getCustomerId());
        if (cartItemProducts.isEmpty()) {
            throw new NotFoundCartItemException("Not found cart items for purchase");
        }
        int totalAmount = cartItemProducts.stream().mapToInt(c -> c.getProductPrice().intValue()).sum();

        model.addAttribute("cartItemProducts", cartItemProducts);
        model.addAttribute(ATTRIBUTE_NAME_CART_ID, cartId);
        model.addAttribute("customer", CustomerDTO.of(customer));
        model.addAttribute("totalAmount", totalAmount);

        return "/checkout/order-checkout";
    }
}
