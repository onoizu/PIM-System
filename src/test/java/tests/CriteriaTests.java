package tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import model.criteria.AndCriterion;
import model.criteria.Criterion;
import model.criteria.CriterionParser;
import model.criteria.NotCriterion;
import model.criteria.OrCriterion;
import model.criteria.TextCriterion;
import model.criteria.TimeCriterion;
import model.criteria.TimeOperator;
import model.criteria.TypeCriterion;
import model.exceptions.CriterionParseException;
import model.exceptions.ValidationException;
import model.records.Contact;
import model.records.Event;
import model.records.FieldName;
import model.records.Note;
import model.records.RecordType;
import model.records.Task;
import org.junit.Test;

public final class CriteriaTests {
    private static final CriterionParser PARSER = new CriterionParser();

    // Search Event 23 before and after its reschedule.
    @Test
    public void eventSearch() throws Exception {
        String query = "type = event && description contains \"consultation\""
                + " && (start < \"2026-11-18 00:00\" || alarm = \"2026-11-16 13:30\")";
        Criterion condition = PARSER.parse(query);
        Event original = new Event(23, "COMP3211 project consultation",
                LocalDateTime.of(2026, 11, 16, 14, 0), LocalDateTime.of(2026, 11, 16, 13, 30));
        Event revised = new Event(23, original.getDescription(),
                LocalDateTime.of(2026, 11, 18, 16, 0), LocalDateTime.of(2026, 11, 18, 15, 30));

        assertTrue("The original consultation matches the search.", condition.matches(original));
        assertFalse("After rescheduling, both time comparisons are false.", condition.matches(revised));
    }

    // Match record types and case-sensitive text.
    @Test
    public void typeAndText() throws Exception {
        Note note = new Note(1, "Quick reminder");
        Task task = new Task(2, "Course project", LocalDateTime.of(2026, 11, 20, 20, 0));
        Contact contact = new Contact(3, "Alex Lee", "Room A-102", "+0012345");

        assertTrue("Match a note.", PARSER.parse("type=note && text contains \"reminder\"").matches(note));
        assertTrue("Match a task.", PARSER.parse("type=task && description contains \"project\"").matches(task));
        assertTrue("Match all three contact fields.",
                PARSER.parse("type=contact && name contains \"Alex\" && address contains \"A-102\""
                + " && mobile contains \"+00\"").matches(contact));
        assertFalse("Text matching is case sensitive.", PARSER.parse("text contains \"quick\"").matches(note));
        assertTrue("A space is a nonempty fragment.", PARSER.parse("text contains \" \"").matches(note));
    }

    // Compare times with strict before, after and equality.
    @Test
    public void timeComparisons() throws Exception {
        Task task = new Task(2, "Course project", LocalDateTime.of(2026, 11, 20, 20, 0));
        assertTrue("Earlier minute matches.", PARSER.parse("deadline < \"2026-11-20 20:01\"").matches(task));
        assertFalse("Equality is not before.", PARSER.parse("deadline < \"2026-11-20 20:00\"").matches(task));
        assertTrue("Later minute matches.", PARSER.parse("deadline > \"2026-11-20 19:59\"").matches(task));
        assertFalse("Equality is not after.", PARSER.parse("deadline > \"2026-11-20 20:00\"").matches(task));
        assertTrue("Exact minute matches.", PARSER.parse("deadline = \"2026-11-20 20:00\"").matches(task));
        assertFalse("Different minute fails.", PARSER.parse("deadline = \"2026-11-20 20:01\"").matches(task));
    }

    // Respect Boolean precedence and grouping.
    @Test
    public void precedence() throws Exception {
        Note note = new Note(1, "Quick reminder");
        assertTrue("AND binds more tightly than OR.",
                PARSER.parse("type = note || type = task && type = event").matches(note));
        assertFalse("Parentheses change the order of evaluation.",
                PARSER.parse("(type = note || type = task) && type = event").matches(note));
        assertFalse("NOT binds to the first predicate.",
                PARSER.parse("!type = note && type = event").matches(note));
        assertTrue("Parentheses give NOT a composite operand.",
                PARSER.parse("!(type = note && type = event)").matches(note));
    }

    // Exercise short-circuit evaluation for both Boolean operators.
    @Test
    public void shortCircuit() throws ValidationException {
        Note note = new Note(1, "Quick reminder");
        Criterion matchesNotes = new TypeCriterion(RecordType.NOTE);
        Criterion matchesTasks = new TypeCriterion(RecordType.TASK);
        Criterion unreachable = record -> {
            throw new AssertionError("Unexpected right operand evaluation for record " + record.getId());
        };
        assertFalse("A false left operand short-circuits AND.",
                new AndCriterion(matchesTasks, unreachable).matches(note));
        assertTrue("A true left operand short-circuits OR.",
                new OrCriterion(matchesNotes, unreachable).matches(note));
        assertFalse("AND evaluates a false right operand when the left operand is true.",
                new AndCriterion(matchesNotes, matchesTasks).matches(note));
        assertTrue("OR evaluates a true right operand when the left operand is false.",
                new OrCriterion(matchesTasks, matchesNotes).matches(note));
    }

    // Treat absent fields as false before negation.
    @Test
    public void absentFields() throws Exception {
        Note note = new Note(1, "Quick reminder");
        assertFalse("Note has no description.", PARSER.parse("description contains \"reminder\"").matches(note));
        assertFalse("Note has no start time.", PARSER.parse("start < \"2026-11-18 00:00\"").matches(note));
        assertTrue("NOT inverts the false result.", PARSER.parse("!(start < \"2026-11-18 00:00\")").matches(note));
        assertFalse("A type guard excludes the note.",
                PARSER.parse("type = event && !(start < \"2026-11-18 00:00\")").matches(note));
    }

    // Preserve quoted text and supported escapes.
    @Test
    public void quotedValues() throws Exception {
        Note note = new Note(1, "Path \"A\\B\" || fallback");
        Criterion criterion = PARSER.parse("text contains \"\\\"A\\\\B\\\" || fallback\"");
        assertTrue("Escapes and operator characters stay in the quoted value.", criterion.matches(note));
    }

    // Reject malformed queries with a position and reason.
    @Test
    public void invalidQueries() {
        rejectQuery(null, "expression is required");
        rejectQuery("", "field name");
        rejectQuery("type = unknown", "Unknown record type");
        rejectQuery("phone contains \"123\"", "Unknown field");
        rejectQuery("type > event", "Expected '='");
        rejectQuery("text = \"x\"", "contains");
        rejectQuery("text is \"x\"", "contains");
        rejectQuery("text contains x", "quoted text");
        rejectQuery("text contains \"\"", "must not be empty");
        rejectQuery("start contains \"2026-11-18 00:00\"", "Expected '<'");
        rejectQuery("start <= \"2026-11-18 00:00\"", "quoted date");
        rejectQuery("start = \"2026-11-18 00:00:00\"", "format");
        rejectQuery("text contains \"x\\n\"", "can be escaped");
        rejectQuery("text contains \"x\\", "Incomplete escape");
        rejectQuery("text contains \"x", "Unterminated");
        rejectQuery("type = event & type = note", "&&");
        rejectQuery("type = event | type = note", "||");
        rejectQuery("type = event ||", "field name");
        rejectQuery("(type = event", "Expected ')'");
        rejectQuery("type = event )", "Unexpected token");
        rejectQuery("type = event ;", "Unexpected character");

        CriterionParseException invalidDate = assertThrows(CriterionParseException.class,
                () -> PARSER.parse("start = \"2026-02-29 00:00\""));
        assertTrue("Point to the invalid time literal.", invalidDate.getMessage().startsWith("Position 9:"));
        assertEquals(ValidationException.class, invalidDate.getCause().getClass());
    }

    // Bound query length, token count and nesting.
    @Test
    public void limits() throws Exception {
        Note note = new Note(1, "Quick reminder");
        String literal = "\uD83D\uDCA1".repeat(2040);
        String lengthLimit = "text contains \"" + literal + "\"";
        assertEquals(4096, lengthLimit.length());
        assertTrue("Accept exactly 4096 UTF-16 units.", PARSER.parse(lengthLimit).matches(new Note(2, literal)));
        rejectQuery(lengthLimit + " ", "4096 UTF-16");

        String tokenLimit = "!type = event" + " || type = task".repeat(63);
        assertTrue("Accept exactly 256 input tokens.", PARSER.parse(tokenLimit).matches(note));
        rejectQuery("!" + tokenLimit, "256 tokens");

        String depthLimit = "!(".repeat(16) + "type = note" + ")".repeat(16);
        assertTrue("Accept 32 combined unary and grouping levels.", PARSER.parse(depthLimit).matches(note));
        rejectQuery("!" + depthLimit, "32 nesting");
    }

    // Reject invalid criterion constructor arguments.
    @Test
    public void constructorValidation() throws ValidationException {
        LocalDateTime minute = LocalDateTime.of(2026, 11, 20, 20, 0);
        Criterion always = new TypeCriterion(RecordType.NOTE);
        assertThrows(ValidationException.class, () -> new TypeCriterion(null));
        assertThrows(ValidationException.class, () -> new TextCriterion(null, "text"));
        assertThrows(ValidationException.class, () -> new TextCriterion(FieldName.START, "text"));
        assertThrows(ValidationException.class, () -> new TextCriterion(FieldName.TEXT, null));
        assertThrows(ValidationException.class, () -> new TimeCriterion(FieldName.TEXT, TimeOperator.EQUAL, minute));
        assertThrows(ValidationException.class, () -> new TimeCriterion(FieldName.START, null, minute));
        assertThrows(ValidationException.class,
                () -> new TimeCriterion(FieldName.START, TimeOperator.EQUAL, minute.plusSeconds(1)));
        assertThrows(ValidationException.class, () -> new AndCriterion(null, always));
        assertThrows(ValidationException.class, () -> new AndCriterion(always, null));
        assertThrows(ValidationException.class, () -> new OrCriterion(null, always));
        assertThrows(ValidationException.class, () -> new OrCriterion(always, null));
        assertThrows(ValidationException.class, () -> new NotCriterion(null));
    }

    private static void rejectQuery(String query, String reason) {
        CriterionParseException failure = assertThrows(CriterionParseException.class, () -> PARSER.parse(query));
        assertTrue("Report a position for the rejected query.", failure.getMessage().startsWith("Position "));
        assertTrue("Expected error reason: " + reason, failure.getMessage().contains(reason));
    }
}
