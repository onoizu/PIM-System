package model.records;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Lists the required fields for each supported record type. */
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

    /** The field order is shared by console output and the file format. */
    public List<FieldName> fields() {
        return fields;
    }

    public String commandName() {
        return commandName;
    }

    /** Resolve a type from its exact command spelling. */
    public static Optional<RecordType> fromCommandName(String name) {
        for (RecordType type : values()) {
            if (type.commandName.equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
