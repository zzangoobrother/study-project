package com.example.consensus.sim;

/** {@link Invariant}가 위반을 알릴 때 던지는 예외. Simulation.run()이 잡아 결과에 담는다. */
public class InvariantViolation extends RuntimeException {

    public InvariantViolation(String message) {
        super(message);
    }
}
