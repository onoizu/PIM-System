package model.exceptions;

import java.io.Serial;

public final class CriterionParseException extends PimException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CriterionParseException(String message) {
        super(message);
    }

    public CriterionParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
