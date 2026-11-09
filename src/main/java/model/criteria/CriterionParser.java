package model.criteria;

import java.util.ArrayList;
import java.util.List;
import model.exceptions.CriterionParseException;
import model.exceptions.ValidationException;
import model.records.FieldName;
import model.records.RecordTime;
import model.records.RecordType;

/** Turns a search expression into a criterion the record book can evaluate. */
public final class CriterionParser {
    private static final int MAX_EXPRESSION_LENGTH = 4096;
    private static final int MAX_TOKENS = 256;
    private static final int MAX_DEPTH = 32;

    public Criterion parse(String expression) throws CriterionParseException {
        if (expression == null) {
            throw error(0, "An expression is required.");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw error(MAX_EXPRESSION_LENGTH,
                    "Expression exceeds " + MAX_EXPRESSION_LENGTH + " UTF-16 code units.");
        }
        return new Parser(new Lexer(expression).tokenize()).parse();
    }

    private static CriterionParseException error(int offset, String reason) {
        return new CriterionParseException(atPosition(offset, reason));
    }

    private static String atPosition(int offset, String reason) {
        return "Position " + (offset + 1) + ": " + reason;
    }

    private enum Kind {
        WORD, QUOTED, EQUAL, LESS, GREATER, AND, OR, NOT, OPEN, CLOSE, END
    }

    private record Token(Kind kind, String text, int offset) {
    }

    private static final class Lexer {
        private final String expression;
        private int offset;

        private Lexer(String expression) {
            this.expression = expression;
        }

        private List<Token> tokenize() throws CriterionParseException {
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

        private Kind readBooleanOperator(char first, int start) throws CriterionParseException {
            if (offset >= expression.length() || expression.charAt(offset) != first) {
                throw error(start, "Use '" + first + first + "' for a Boolean operator.");
            }
            offset++;
            return first == '&' ? Kind.AND : Kind.OR;
        }

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
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int index;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private Criterion parse() throws CriterionParseException {
            try {
                Criterion result = parseOr(0);
                expect(Kind.END, "Unexpected token after the expression.");
                return result;
            } catch (ValidationException exception) {
                // Predicate validation follows consumption of its value.
                int offset = tokens.get(index - 1).offset();
                throw new CriterionParseException(atPosition(offset, exception.getMessage()), exception);
            }
        }

        // Parse OR last by letting each operand consume its AND expressions first.
        private Criterion parseOr(int depth) throws CriterionParseException, ValidationException {
            Criterion result = parseAnd(depth);
            while (accept(Kind.OR)) {
                result = new OrCriterion(result, parseAnd(depth));
            }
            return result;
        }

        private Criterion parseAnd(int depth) throws CriterionParseException, ValidationException {
            Criterion result = parseUnary(depth);
            while (accept(Kind.AND)) {
                result = new AndCriterion(result, parseUnary(depth));
            }
            return result;
        }

        private Criterion parseUnary(int depth) throws CriterionParseException, ValidationException {
            if (depth > MAX_DEPTH) {
                throw error(tokens.get(index - 1).offset(),
                        "Expression exceeds " + MAX_DEPTH + " nesting levels.");
            }
            if (accept(Kind.NOT)) {
                return new NotCriterion(parseUnary(depth + 1));
            }
            if (accept(Kind.OPEN)) {
                Criterion nested = parseOr(depth + 1);
                expect(Kind.CLOSE, "Expected ')'.");
                return nested;
            }
            return parsePredicate();
        }

        private Criterion parsePredicate() throws CriterionParseException, ValidationException {
            Token name = expect(Kind.WORD, "Expected a field name.");
            if (name.text().equals("type")) {
                expect(Kind.EQUAL, "Expected '=' after 'type'.");
                Token value = expect(Kind.WORD, "Expected a record type.");
                RecordType type = RecordType.fromCommandName(value.text())
                        .orElseThrow(() -> error(value.offset(),
                                "Unknown record type '" + value.text() + "'."));
                return new TypeCriterion(type);
            }

            FieldName field = FieldName.fromCommandName(name.text())
                    .orElseThrow(() -> error(name.offset(), "Unknown field '" + name.text() + "'."));
            if (field.isText()) {
                Token operator = expect(Kind.WORD, "Expected 'contains' after a text field.");
                if (!operator.text().equals("contains")) {
                    throw error(operator.offset(), "Expected 'contains' after a text field.");
                }
                Token value = expect(Kind.QUOTED, "Expected a quoted text value.");
                return new TextCriterion(field, value.text());
            }

            TimeOperator operator = parseTimeOperator();
            Token value = expect(Kind.QUOTED, "Expected a quoted date and time.");
            return new TimeCriterion(field, operator, RecordTime.parse(value.text()));
        }

        private TimeOperator parseTimeOperator() throws CriterionParseException {
            Token token = tokens.get(index);
            TimeOperator operator = switch (token.kind()) {
                case LESS -> TimeOperator.BEFORE;
                case GREATER -> TimeOperator.AFTER;
                case EQUAL -> TimeOperator.EQUAL;
                default -> throw error(token.offset(), "Expected '<', '>' or '=' after a time field.");
            };
            index++;
            return operator;
        }

        private Token expect(Kind kind, String reason) throws CriterionParseException {
            Token token = tokens.get(index);
            if (!accept(kind)) {
                throw error(token.offset(), reason);
            }
            return token;
        }

        private boolean accept(Kind kind) {
            if (tokens.get(index).kind() != kind) {
                return false;
            }
            index++;
            return true;
        }
    }
}
