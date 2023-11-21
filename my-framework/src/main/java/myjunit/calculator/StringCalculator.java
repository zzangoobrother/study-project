package myjunit.calculator;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringCalculator {
    public int add(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        String separate = ",|:";

        Matcher matcher = Pattern.compile("//(.)\n(.*)").matcher(text);
        if (matcher.find()) {
            String customDelimeter = matcher.group(1);
            separate = separate + "|" + customDelimeter;
            text = matcher.group(2);
        }

        String[] numbers = text.split(separate);
        Arrays.stream(numbers)
                .mapToInt(t -> Integer.parseInt(t))
                .forEach(num -> {
                    if (num < 0) throw new RuntimeException();
                });

        return Arrays.stream(numbers).mapToInt(t -> Integer.parseInt(t)).sum();
    }


}
