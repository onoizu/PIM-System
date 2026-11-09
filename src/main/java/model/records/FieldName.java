package model.records;

import java.util.Locale;
import java.util.Optional;

public enum FieldName {
    TEXT(true),
    DESCRIPTION(true),
    DEADLINE(false),
    START(false),
    ALARM(false),
    NAME(true),
    ADDRESS(true),
    MOBILE(true);

    private final boolean text;
    private final String commandName;

    FieldName(boolean text) {
        this.text = text;
        this.commandName = name().toLowerCase(Locale.ROOT);
    }

    public boolean isText() {
        return text;
    }

    public boolean isTime() {
        return !text;
    }

    public String commandName() {
        return commandName;
    }

    public static Optional<FieldName> fromCommandName(String name) {
        for (FieldName field : values()) {
            if (field.commandName.equals(name)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }
}
