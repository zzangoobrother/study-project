package sample.cafekiosk.spring.api.service.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import sample.cafekiosk.spring.IntegrationTestSupport;
import sample.cafekiosk.spring.domain.history.MailSendHistory;
import sample.cafekiosk.spring.domain.history.MailSendHistoryRepository;
import sample.cafekiosk.spring.domain.order.Order;
import sample.cafekiosk.spring.domain.order.OrderRepository;
import sample.cafekiosk.spring.domain.order.OrderStatus;
import sample.cafekiosk.spring.domain.orderproduct.OrderProductRepository;
import sample.cafekiosk.spring.domain.product.Product;
import sample.cafekiosk.spring.domain.product.ProductRepository;
import sample.cafekiosk.spring.domain.product.ProductSellingStatus;
import sample.cafekiosk.spring.domain.product.ProductType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class OrderStatisticsServiceTest extends IntegrationTestSupport {

    @Autowired
    private OrderStatisticsService orderStatisticsService;

    @Autowired
    private OrderProductRepository orderProductRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MailSendHistoryRepository mailSendHistoryRepository;

    @AfterEach
    void tearDown() {
        orderProductRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        mailSendHistoryRepository.deleteAllInBatch();
    }

    @DisplayName("결제완료 주문들을 조회하여 매출 통계 메일을 전송한다.")
    @Test
    void sendOrderStatisticsMail() {
        // given
        LocalDateTime now = LocalDateTime.of(2023, 7, 4, 00, 0);

        Product product1 = createProduct("001", ProductType.BOTTLE, 4000);
        Product product2 = createProduct("002", ProductType.BAKERY, 4500);
        Product product3 = createProduct("003", ProductType.HANDMADE, 7000);

        List<Product> products = List.of(product1, product2, product3);
        productRepository.saveAll(products);

        Order order1 = createPaymentCompletedOrder(LocalDateTime.of(2023, 7, 3, 23, 59, 59), products);
        Order order2 = createPaymentCompletedOrder(now, products);
        Order order3 = createPaymentCompletedOrder(LocalDateTime.of(2023, 7, 4, 23, 59, 59), products);
        Order order4 = createPaymentCompletedOrder(LocalDateTime.of(2023, 7, 5, 0, 0), products);

        Mockito.when(mailSendClient.sendEmail(any(String.class), any(String.class), any(String.class), any(String.class))).thenReturn(true);

        // when
        boolean result = orderStatisticsService.sendOrderStatisticsMail(LocalDate.of(2023, 7, 4), "test@test.com");

        // then
        assertThat(result).isTrue();

        List<MailSendHistory> histories = mailSendHistoryRepository.findAll();
        assertThat(histories).hasSize(1)
                .extracting("content")
                .contains("총 매출 합계는 31000원 입니다.");
    }

    private Order createPaymentCompletedOrder(LocalDateTime now, List<Product> products) {
        Order order = Order.builder()
                .products(products)
                .orderStatus(OrderStatus.PAYMENT_COMPLETED)
                .registeredDateTime(now)
                .build();
        return orderRepository.save(order);
    }

    private Product createProduct(String productNumber, ProductType type, int price) {
        return Product.builder()
                .productNumber(productNumber)
                .type(type)
                .sellingStatus(ProductSellingStatus.SELLING)
                .name("메뉴 이름")
                .price(price)
                .build();
    }
}
