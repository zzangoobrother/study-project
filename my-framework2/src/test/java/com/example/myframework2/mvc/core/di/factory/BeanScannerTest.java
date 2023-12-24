package com.example.myframework2.mvc.core.di.factory;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class BeanScannerTest {
    private Logger log = LoggerFactory.getLogger(BeanScannerTest.class);

    @Test
    void scan() {
        BeanScanner scanner = new BeanScanner("com.example.myframework2.mvc.core.di.factory.example", "com.example.myframework2.mvc.core.nmvc");
        Set<Class<?>> beans = scanner.scan();
        for (Class<?> clazz : beans) {
            log.debug("Bean : {}", clazz);
        }
    }
}
