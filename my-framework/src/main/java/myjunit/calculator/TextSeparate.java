package myjunit.calculator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextSeparate {

    public String[] separate(String text) {
        String separate = ",|:";

        Matcher matcher = Pattern.compile("//(.)\n(.*)").matcher(text);
        if (matcher.find()) {
            String customDelimeter = matcher.group(1);
            separate = separate + "|" + customDelimeter;
            text = matcher.group(2);
        }

        return text.split(separate);
    }
}
