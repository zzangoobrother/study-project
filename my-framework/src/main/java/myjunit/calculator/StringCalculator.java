package myjunit.calculator;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringCalculator {
    public int add(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        if (text.length() == 1) {
            return Integer.parseInt(text);
        }

        Matcher matcher = Pattern.compile("//(.)\n(.*)").matcher(text);
        if (matcher.find()) {
            String customDelimeter = matcher.group(1);
            String[] numbers = matcher.group(2).split(customDelimeter);
            return Arrays.stream(numbers).mapToInt(t -> Integer.parseInt(t)).sum();
        }

        String[] numbers = text.split(",|:");
        return Arrays.stream(numbers).mapToInt(t -> Integer.parseInt(t)).sum();
    }
}
