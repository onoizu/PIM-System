package model.records;

import java.time.LocalDateTime;
import java.util.Optional;
import model.exceptions.ValidationException;

public abstract sealed class PIR permits Note, Task, Event, Contact {
    private final long id;
    private final RecordType type;

    protected PIR(long id, RecordType type) throws ValidationException {
        if (id <= 0) {
            throw new ValidationException("Record IDs must be positive.");
        }
        if (type == null) {
            throw new ValidationException("Record type is required.");
        }
        this.id = id;
        this.type = type;
    }

    public final long getId() {
        return id;
    }

    public final RecordType getType() {
        return type;
    }

    public abstract Optional<String> getTextField(FieldName field);

    public Optional<LocalDateTime> getTimeField(FieldName field) {
        return Optional.empty();
    }

    static String requireText(FieldName field, String value) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Field '" + field.commandName() + "' must not be blank.");
        }

        // Reject unpaired surrogates so every accepted value can be saved as UTF-8.
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isSurrogate(character)) {
                continue;
            }
            if (!Character.isHighSurrogate(character) || index + 1 == value.length()
                    || !Character.isLowSurrogate(value.charAt(index + 1))) {
                throw new ValidationException("Field '" + field.commandName()
                        + "' must contain valid Unicode text.");
            }
            index++;
        }
        return value;
    }
}
