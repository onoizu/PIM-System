package model.exceptions;

import java.io.Serial;

public final class RecordNotFoundException extends PimException {
    @Serial
    private static final long serialVersionUID = 1L;

    public RecordNotFoundException(String message) {
        super(message);
    }

    public RecordNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
