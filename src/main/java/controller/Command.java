package controller;

import java.util.Objects;

public final class Command {
    private final CommandType type;
    private final String body;

    public Command(CommandType type, String body) {
        this.type = Objects.requireNonNull(type, "type");
        this.body = Objects.requireNonNull(body, "body");
    }

    public CommandType getType() {
        return type;
    }

    public String getBody() {
        return body;
    }
}
