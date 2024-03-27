package com.example.controller.order;

import com.example.controller.dto.order.DirectOrderRequestDTO;
import com.example.controller.dto.order.OrderRequestDTO;
import com.example.domain.cart.CartItemProduct;
import com.example.domain.customer.Customer;
import com.example.domain.customer.CustomerDetail;
import com.example.exception.NotFoundCustomerException;
import com.example.service.cart.CartService;
import com.example.service.order.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class OrderController {

    private final CartService cartService;
    private final OrderService orderService;

    public OrderController(CartService cartService, OrderService orderService) {
        this.cartService = cartService;
        this.orderService = orderService;
    }

    @PostMapping(value = "/order")
    public String order(@AuthenticationPrincipal CustomerDetail customerDetail, OrderRequestDTO orderRequest, RedirectAttributes redirectAttributes) {
        log.info(">>> 주문 처리, 고객 정보, {}", customerDetail);
        if (Objects.isNull(customerDetail)) {
            throw new NotFoundCustomerException("고객 정보를 찾을 수 없습니다.");
        }
        Customer customer = customerDetail.getCustomer();

        List<CartItemProduct> cartItems = cartService.getCartItems(customer.getCustomerId());
        List<Long> cartItemIds = cartItems.stream().map(CartItemProduct::getCartItemId).collect(Collectors.toList());
        Long orderId = orderService.order(customer, cartItemIds, orderRequest.getPayType());
        cartService.empty(customer.getCustomerId());

        redirectAttributes.addFlashAttribute("orderId", orderId);

        return "redirect:/thanks";
    }

    @PostMapping(value = "/direct-order")
    public String directOrder(@AuthenticationPrincipal CustomerDetail customerDetail, DirectOrderRequestDTO directOrderRequestDTO, RedirectAttributes redirectAttributes) {
        log.info(">>> 주문 처리, 고객 정보, {}", customerDetail);
        if (Objects.isNull(customerDetail)) {
            throw new NotFoundCustomerException("고객 정보를 찾을 수 없습니다.");
        }
        Customer customer = customerDetail.getCustomer();

        Long orderId = orderService.directOrder(customer, directOrderRequestDTO.getProductId(), directOrderRequestDTO.getPayType());
        redirectAttributes.addFlashAttribute("orderId", orderId);

        return "redirect:/thanks";
    }
}
