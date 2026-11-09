package controller;

import java.io.Serial;

import model.exceptions.PimException;

public final class CommandParseException extends PimException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CommandParseException(String message) {
        super(message);
    }

    public CommandParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
