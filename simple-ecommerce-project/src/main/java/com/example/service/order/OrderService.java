package com.example.service.order;

import com.example.domain.cart.CartItemProduct;
import com.example.domain.customer.Customer;
import com.example.domain.order.Order;
import com.example.domain.order.OrderItem;
import com.example.domain.product.Product;
import com.example.enums.PayType;
import com.example.exception.NotFoundProductException;
import com.example.repository.cart.CartItemRepository;
import com.example.repository.customer.CustomerRepository;
import com.example.repository.order.OrderItemRepository;
import com.example.repository.order.OrderRepository;
import com.example.service.product.ProductService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrderService {

    public static final int DIRECT_ORDER_QUANTITY = 1;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository, CartItemRepository cartItemRepository, CustomerRepository customerRepository, ProductService productService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.customerRepository = customerRepository;
        this.productService = productService;
    }

    public Long order(Customer customer, List<Long> cartItems, PayType payType) {
        // Total Amount
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<CartItemProduct> targetCartItems = cartItemRepository.findAllCartItems(cartItems);
        for (CartItemProduct cartItemProduct : targetCartItems) {
            totalAmount = totalAmount.add(cartItemProduct.getProductPrice().multiply(new BigDecimal(cartItemProduct.getQuantity())));
        }

        // Order
        Order savedOrder = createOrder(customer, totalAmount, payType);

        // Order Items
        createOrderItems(targetCartItems, savedOrder);

        return savedOrder.getOrderId();
    }

    private Order createOrder(Customer customer, BigDecimal totalAmount, PayType payType) {
        Order order = new Order();
        order.setCustomerId(customer.getCustomerId());
        order.setDeliveryAddress(customer.getAddress());
        order.setAmount(totalAmount);
        order.setPayType(payType);
        return orderRepository.save(order);
    }

    private List<OrderItem> createOrderItems(List<CartItemProduct> targetCartItems, Order order) {
        List<OrderItem> orderItems = targetCartItems.stream().map(cartItemProduct -> {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getOrderId());
            orderItem.setProductId(cartItemProduct.getProductId());
            orderItem.setPrice(cartItemProduct.getProductPrice());
            orderItem.setQuantity(cartItemProduct.getQuantity());
            return orderItem;
        }).collect(Collectors.toList());
        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);
        return savedOrderItems;
    }

    public Long directOrder(Customer customer, Long productId, PayType payType) {
        Optional<Product> optionalProduct = this.productService.findById(productId);
        if (optionalProduct.isEmpty()) {
            throw new NotFoundProductException("구매하려는 상품 정보를 찾을 수 없습니다.");
        }
        Product product = optionalProduct.get();

        // Total Amount
        BigDecimal totalAmount = product.getPrice();

        // Order
        Order savedOrder = createOrder(customer, totalAmount, payType);

        // Order Item
        OrderItem orderItem = createOrderItem(savedOrder.getOrderId(), product);
        this.orderItemRepository.save(orderItem);


        return savedOrder.getOrderId();
    }

    private OrderItem createOrderItem(Long orderId, Product product) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setProductId(product.getId());
        orderItem.setPrice(product.getPrice());
        orderItem.setQuantity(DIRECT_ORDER_QUANTITY);
        return orderItem;
    }
}
