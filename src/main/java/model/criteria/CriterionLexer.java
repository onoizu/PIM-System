package model.criteria;

import java.util.ArrayList;
import java.util.List;
import model.exceptions.CriterionParseException;

/** Splits a search expression into tokens with their source positions. */
final class CriterionLexer {
    private static final int MAX_TOKENS = 256;

    private final String expression;
    private int offset;

    CriterionLexer(String expression) {
        this.expression = expression;
    }

    // Skip spaces between tokens and append an END marker at the final offset.
    List<Token> tokenize() throws CriterionParseException {
        List<Token> tokens = new ArrayList<>();
        while (offset < expression.length()) {
            if (Character.isWhitespace(expression.charAt(offset))) {
                offset++;
                continue;
            }
            if (tokens.size() == MAX_TOKENS) {
                throw error(offset, "Expression exceeds " + MAX_TOKENS + " tokens.");
            }
            tokens.add(nextToken());
        }
        tokens.add(new Token(Kind.END, "", offset));
        return tokens;
    }

    // Read one word, quoted value, operator or parenthesis.
    private Token nextToken() throws CriterionParseException {
        int start = offset;
        char first = expression.charAt(offset++);
        if (isWordCharacter(first)) {
            while (offset < expression.length()
                    && isWordCharacter(expression.charAt(offset))) {
                offset++;
            }
            return new Token(Kind.WORD, expression.substring(start, offset), start);
        }
        if (first == '"') {
            return readQuoted(start);
        }

        Kind kind = switch (first) {
            case '=' -> Kind.EQUAL;
            case '<' -> Kind.LESS;
            case '>' -> Kind.GREATER;
            case '!' -> Kind.NOT;
            case '(' -> Kind.OPEN;
            case ')' -> Kind.CLOSE;
            case '&', '|' -> readBooleanOperator(first, start);
            default -> throw error(start, "Unexpected character '" + first + "'.");
        };
        return new Token(kind, expression.substring(start, offset), start);
    }

    // The grammar requires two consecutive '&' or '|' characters.
    private Kind readBooleanOperator(char first, int start) throws CriterionParseException {
        if (offset >= expression.length() || expression.charAt(offset) != first) {
            throw error(start, "Use '" + first + first + "' for a Boolean operator.");
        }
        offset++;
        return first == '&' ? Kind.AND : Kind.OR;
    }

    // Preserve spaces and operators inside quotes. Decode the two allowed escapes.
    private Token readQuoted(int start) throws CriterionParseException {
        StringBuilder value = new StringBuilder();
        while (offset < expression.length()) {
            char character = expression.charAt(offset++);
            if (character == '"') {
                return new Token(Kind.QUOTED, value.toString(), start);
            }
            if (character == '\\') {
                int escapeOffset = offset - 1;
                if (offset == expression.length()) {
                    throw error(escapeOffset, "Incomplete escape in quoted value.");
                }
                character = expression.charAt(offset++);
                if (character != '"' && character != '\\') {
                    throw error(escapeOffset, "Only quotes and backslashes can be escaped.");
                }
            }
            value.append(character);
        }
        throw error(start, "Unterminated quoted value.");
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    static CriterionParseException error(int offset, String reason) {
        return new CriterionParseException(atPosition(offset, reason));
    }

    // Convert zero-based offsets to the positions shown to the user.
    static String atPosition(int offset, String reason) {
        return "Position " + (offset + 1) + ": " + reason;
    }

    enum Kind {
        WORD, QUOTED, EQUAL, LESS, GREATER, AND, OR, NOT, OPEN, CLOSE, END
    }

    record Token(Kind kind, String text, int offset) {
    }
}
