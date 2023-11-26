package myjunit.test;

import myjunit.test.error.AssertionFailedError;

import java.util.Objects;

import static myjunit.test.TryCatchThrowable.catchThrowable;

public class Assert {

    private Assert() {}

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionFailedError();
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
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

    public static void assertEquals(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionFailedError();
        }
    }

    public static void assertThatThrownBy(ThrowingCallable shouldRaiseThrowable) {
        assertTrue(catchThrowable(shouldRaiseThrowable) instanceof RuntimeException);
    }
}
