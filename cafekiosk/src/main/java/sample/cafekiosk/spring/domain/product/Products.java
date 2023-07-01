package sample.cafekiosk.spring.domain.product;

import lombok.Getter;

import java.util.List;

@Getter
public class Products {

    private final List<Product> products;

    public Products(List<Product> products) {
        this.products = products;
    }

    public List<String> extractStockProductNumbers() {
        return products.stream()
                .filter(product -> ProductType.containsStockType(product.getType()))
                .map(Product::getProductNumber)
                .toList();
    }
}
