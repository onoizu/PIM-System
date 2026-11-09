package model.records;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum RecordType {
    NOTE(FieldName.TEXT),
    TASK(FieldName.DESCRIPTION, FieldName.DEADLINE),
    EVENT(FieldName.DESCRIPTION, FieldName.START, FieldName.ALARM),
    CONTACT(FieldName.NAME, FieldName.ADDRESS, FieldName.MOBILE);

    private final List<FieldName> fields;
    private final String commandName;

    RecordType(FieldName... fields) {
        this.fields = List.of(fields);
        this.commandName = name().toLowerCase(Locale.ROOT);
    }

    public List<FieldName> fields() {
        return fields;
    }

    public String commandName() {
        return commandName;
    }

    public static Optional<RecordType> fromCommandName(String name) {
        for (RecordType type : values()) {
            if (type.commandName.equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
