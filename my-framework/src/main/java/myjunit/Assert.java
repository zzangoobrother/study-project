package myjunit;

import myjunit.error.AssertionFailedError;

public class Assert {

    private Assert() {}

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionFailedError();
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionFailedError();
        }
    }
}
