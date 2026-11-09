package model.criteria;

import static model.criteria.CriterionLexer.atPosition;
import static model.criteria.CriterionLexer.error;

import java.util.List;
import model.criteria.CriterionLexer.Kind;
import model.criteria.CriterionLexer.Token;
import model.exceptions.CriterionParseException;
import model.exceptions.ValidationException;
import model.records.FieldName;
import model.records.RecordTime;
import model.records.RecordType;

/** Turns a search expression into a criterion the record book can evaluate. */
public final class CriterionParser {
    private static final int MAX_EXPRESSION_LENGTH = 4096;
    private static final int MAX_DEPTH = 32;

    /** Check the input length, then build a condition tree from the tokens. */
    public Criterion parse(String expression) throws CriterionParseException {
        if (expression == null) {
            throw error(0, "An expression is required.");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw error(MAX_EXPRESSION_LENGTH,
                    "Expression exceeds " + MAX_EXPRESSION_LENGTH + " UTF-16 code units.");
        }
        List<Token> tokens = new CriterionLexer(expression).tokenize();
        return new Parser(tokens).parse();
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int index;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        // Require the whole input to form a single expression.
        private Criterion parse() throws CriterionParseException {
            try {
                Criterion result = parseOr(0);
                expect(Kind.END, "Unexpected token after the expression.");
                return result;
            } catch (ValidationException exception) {
                // A rejected value has already been consumed, so point to the previous token.
                int offset = tokens.get(index - 1).offset();
                throw new CriterionParseException(atPosition(offset, exception.getMessage()), exception);
            }
        }

        // Each OR operand may be a complete AND expression.
        private Criterion parseOr(int depth) throws CriterionParseException, ValidationException {
            Criterion result = parseAnd(depth);
            while (accept(Kind.OR)) {
                result = new OrCriterion(result, parseAnd(depth));
            }
            return result;
        }

        // Parse negations and groups before joining them with AND.
        private Criterion parseAnd(int depth) throws CriterionParseException, ValidationException {
            Criterion result = parseUnary(depth);
            while (accept(Kind.AND)) {
                result = new AndCriterion(result, parseUnary(depth));
            }
            return result;
        }

        // NOT and parentheses add one nesting level. A group restarts at OR.
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

        // The field determines which operator and value are allowed.
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

        // Time comparisons accept only the three single-character operators.
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

        // Consume a required token or report where it was expected.
        private Token expect(Kind kind, String reason) throws CriterionParseException {
            Token token = tokens.get(index);
            if (!accept(kind)) {
                throw error(token.offset(), reason);
            }
            return token;
        }

        // Leave the cursor in place when the optional token is absent.
        private boolean accept(Kind kind) {
            if (tokens.get(index).kind() != kind) {
                return false;
            }
            index++;
            return true;
        }
    }
}
