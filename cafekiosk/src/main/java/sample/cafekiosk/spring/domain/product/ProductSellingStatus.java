package sample.cafekiosk.spring.domain.product;

import lombok.Getter;

import java.util.List;

@Getter
public enum ProductSellingStatus {
    SELLING("판매중"),
    HOLD("판매 보류"),
    STOP_SELLING("판매중지");

    private final String text;

    ProductSellingStatus(String text) {
        this.text = text;
    }

    public static List<ProductSellingStatus> forDisplay() {
        return List.of(SELLING, HOLD);
    }
}
