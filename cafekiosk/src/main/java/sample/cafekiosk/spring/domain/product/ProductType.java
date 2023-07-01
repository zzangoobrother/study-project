package sample.cafekiosk.spring.domain.product;

import lombok.Getter;

import java.util.List;

@Getter
public enum ProductType {
    HANDMADE("제조 음료"),
    BOTTLE("병 음료"),
    BAKERY("베이커리");

    private final String text;

    ProductType(String text) {
        this.text = text;
    }

    public static boolean containsStockType(ProductType type) {
        return List.of(BOTTLE, BAKERY).contains(type);
    }
}
