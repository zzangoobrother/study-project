package sample.cafekiosk.spring.domain.product;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class ProductNumbers {
    private final List<String> productNumbers;

    public ProductNumbers(List<String> productNumbers) {
        this.productNumbers = productNumbers;
    }

    public Map<String, Long> createCountingMapBy() {
        return productNumbers.stream()
                .collect(Collectors.groupingBy(p -> p, Collectors.counting()));
    }
}
