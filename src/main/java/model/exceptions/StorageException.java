package model.exceptions;

import java.io.Serial;

public final class StorageException extends PimException {
    @Serial
    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
