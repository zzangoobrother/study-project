package myjunit.calculator;

import java.util.Arrays;

public class StringCalculator {
    public int add(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        if (text.length() == 1) {
            return Integer.parseInt(text);
        }

        String[] numbers = text.split(",|:");
        return Arrays.stream(numbers).mapToInt(t -> Integer.parseInt(t)).sum();
    }
}
