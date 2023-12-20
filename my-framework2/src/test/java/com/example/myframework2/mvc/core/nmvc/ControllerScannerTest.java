package com.example.myframework2.mvc.core.nmvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

class ControllerScannerTest {
    private static final Logger log = LoggerFactory.getLogger(ControllerScannerTest.class);

    private ControllerScanner cs;

    @BeforeEach
    void setup() {
        cs = new ControllerScanner("com.example.myframework2.mvc.core.nmvc");
    }

    @Test
    void getControllers() {
        Map<Class<?>, Object> controllers = cs.getControllers();

        for (Class<?> controller : controllers.keySet()) {
            log.info("controller : {}", controller);
        }
    }
}
