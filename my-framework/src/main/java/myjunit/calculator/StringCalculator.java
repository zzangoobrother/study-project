package myjunit.calculator;

import java.util.Arrays;

public class StringCalculator {
    public int add(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        TextSeparate textSeparate = new TextSeparate();
        String[] numbers = textSeparate.separate(text);

        Arrays.stream(numbers)
                .mapToInt(t -> Integer.parseInt(t))
                .forEach(num -> {
                    if (num < 0) throw new RuntimeException();
                });

        return Arrays.stream(numbers).mapToInt(t -> Integer.parseInt(t)).sum();
    }


}
