package controller;

import java.util.EnumMap;
import java.util.Map;
import model.records.FieldName;

/** Reads command envelopes and assignments. Search expressions go to the model as text. */
public final class CommandParser {
    public Command parse(String line) throws CommandParseException {
        if (line == null || line.isBlank()) {
            throw new CommandParseException("Enter a command. Type help for usage.");
        }
        String text = line.stripLeading();
        int end = wordEnd(text);
        String name = text.substring(0, end);
        CommandType type = switch (name) {
            case "create" -> CommandType.CREATE;
            case "update" -> CommandType.UPDATE;
            case "search" -> CommandType.SEARCH;
            case "print" -> CommandType.PRINT;
            case "delete" -> CommandType.DELETE;
            case "save" -> CommandType.SAVE;
            case "load" -> CommandType.LOAD;
            case "help" -> CommandType.HELP;
            case "exit" -> CommandType.EXIT;
            default -> throw new CommandParseException("Unknown command: " + name + ". Type help for usage.");
        };
        return new Command(type, text.substring(end).stripLeading());
    }

    public Map<FieldName, String> parseFields(String text) throws CommandParseException {
        if (text == null || text.isBlank()) {
            throw new CommandParseException("Expected at least one field=\"value\" assignment.");
        }
        Cursor cursor = new Cursor(text.stripLeading());
        Map<FieldName, String> fields = new EnumMap<>(FieldName.class);
        while (!cursor.atEnd()) {
            String name = cursor.readFieldName();
            FieldName field = FieldName.fromCommandName(name)
                    .orElseThrow(() -> new CommandParseException("Unknown or read-only field: " + name));
            cursor.expect('=', "Expected '=' immediately after the field name.");
            String value = cursor.readQuoted();
            if (fields.putIfAbsent(field, value) != null) {
                throw new CommandParseException("Duplicate field: " + name);
            }
            if (!cursor.atEnd() && !Character.isWhitespace(cursor.current())) {
                throw cursor.error("Separate field assignments with whitespace.");
            }
            cursor.skipWhitespace();
        }
        return Map.copyOf(fields);
    }

    String parseFileArgument(String text) throws CommandParseException {
        Cursor cursor = new Cursor(text.stripLeading());
        String path = cursor.readQuoted();
        cursor.skipWhitespace();
        if (path.isEmpty() || !cursor.atEnd()) {
            throw cursor.error("Expected exactly one non-empty quoted file path.");
        }
        return path;
    }

    static int wordEnd(String text) {
        int end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static final class Cursor {
        private final String text;
        private int index;

        Cursor(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index == text.length();
        }

        char current() {
            return text.charAt(index);
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(current())) {
                index++;
            }
        }

        String readFieldName() throws CommandParseException {
            int start = index;
            while (!atEnd() && current() >= 'a' && current() <= 'z') {
                index++;
            }
            if (index == start) {
                throw error("Expected a lower-case field name.");
            }
            return text.substring(start, index);
        }

        void expect(char expected, String message) throws CommandParseException {
            if (atEnd() || current() != expected) {
                throw error(message);
            }
            index++;
        }

        String readQuoted() throws CommandParseException {
            expect('"', "Expected a double-quoted value.");
            StringBuilder value = new StringBuilder();
            while (!atEnd()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    if (atEnd() || (current() != '"' && current() != '\\')) {
                        throw error("Only escaped double quotes and backslashes are supported.");
                    }
                    character = text.charAt(index++);
                }
                value.append(character);
            }
            throw error("Missing closing double quote.");
        }

        CommandParseException error(String message) {
            return new CommandParseException(message + " (column " + (index + 1) + ")");
        }
    }
}
