package myjunit.test;

public class TryCatchThrowable {

    public static Throwable catchThrowable(ThrowingCallable shouldRaiseThrowable) {
        try {
            shouldRaiseThrowable.call();
        } catch (Throwable throwable) {
            return new RuntimeException();
        }

        return null;
    }
}
