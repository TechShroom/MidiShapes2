package com.techshroom.midishapes.midi.player;

public class EarlyReturnError extends RuntimeException {

    private static final long serialVersionUID = 1995196096748923467L;

    private static final EarlyReturnError INSTANCE = new EarlyReturnError();

    public static EarlyReturnError getInstance() {
        return INSTANCE;
    }

    private EarlyReturnError() {
        super("Early return from method", null, false, false);
    }

}
