package model.exceptions;

import java.io.Serial;

public class PimException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public PimException(String message) {
        super(message);
    }

    public PimException(String message, Throwable cause) {
        super(message, cause);
    }
}
