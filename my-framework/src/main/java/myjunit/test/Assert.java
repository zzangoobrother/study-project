package myjunit.test;

import myjunit.test.error.AssertionFailedError;

import java.util.Objects;

public class Assert {

    private Assert() {}

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionFailedError();
        }
    }

    public static void assertEquals(Long expected, Long actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionFailedError();
        }
    }

    public static void assertEquals(Integer expected, Integer actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionFailedError();
        }
    }
}
