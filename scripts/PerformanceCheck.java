import controller.CommandParser;
import controller.PimController;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import model.RecordBook;
import model.RecordSnapshot;
import model.criteria.CriterionParser;
import model.persistence.PimFileStorage;
import model.records.FieldName;
import model.records.RecordType;
import view.ConsoleView;

/** Runs the fixed in-memory workload specified by NFR-04. */
class PerformanceCheck {
    private static final int WARMUP_RUNS = 5;
    private static final int TIMED_RUNS = 30;
    private static final long TARGET_NANOS = 2_000_000_000L;

    public static void main(String[] args) throws Exception {
        RecordBook book = new RecordBook();
        for (int index = 0; index < 999; index++) {
            addFixtureRecord(book, index);
        }
        RecordSnapshot beforeCreate = book.snapshot();
        addFixtureRecord(book, 999);
        RecordSnapshot full = book.snapshot();

        CommandParser parser = new CommandParser();
        try (PrintStream output = new PrintStream(
                OutputStream.nullOutputStream(), false, StandardCharsets.UTF_8)) {
            var input = new BufferedReader(new StringReader(""));
            var controller = new PimController(book, new CriterionParser(),
                    new PimFileStorage(), parser, new ConsoleView(input, output));
            check("create", "create note text=\"A new note\"", beforeCreate, book, parser, controller);
            check("update", "update 2 description=\"Revised task\"", full, book, parser, controller);
            check("delete", "delete 1000", full, book, parser, controller);
            check("display one", "print 3", full, book, parser, controller);
            check("display all", "print all", full, book, parser, controller);
            check("search", "search type = event && (description contains \"project\""
                    + " || start > \"2026-11-10 09:00\")", full, book, parser, controller);
        }
    }

    private static void addFixtureRecord(RecordBook book, int index) throws Exception {
        switch (index % 4) {
            case 0 -> book.create(RecordType.NOTE, Map.of(FieldName.TEXT, "Note " + index));
            case 1 -> book.create(RecordType.TASK, Map.of(
                    FieldName.DESCRIPTION, "Task " + index,
                    FieldName.DEADLINE, "2026-11-20 20:00"));
            case 2 -> book.create(RecordType.EVENT, Map.of(
                    FieldName.DESCRIPTION, "Project meeting " + index,
                    FieldName.START, "2026-11-16 14:00",
                    FieldName.ALARM, "2026-11-16 13:30"));
            case 3 -> book.create(RecordType.CONTACT, Map.of(
                    FieldName.NAME, "Contact " + index,
                    FieldName.ADDRESS, "Room 44",
                    FieldName.MOBILE, "+00123"));
            default -> throw new AssertionError(index);
        }
    }

    private static void check(String name, String command, RecordSnapshot fixture,
                              RecordBook book, CommandParser parser, PimController controller)
            throws Exception {
        long maximum = 0;
        for (int run = 0; run < WARMUP_RUNS + TIMED_RUNS; run++) {
            book.replaceState(fixture);
            long start = System.nanoTime();
            controller.execute(parser.parse(command));
            long elapsed = System.nanoTime() - start;
            if (run >= WARMUP_RUNS) {
                maximum = Math.max(maximum, elapsed);
            }
        }
        System.out.printf(Locale.ROOT, "%-12s %4d records  max %8.3f ms%n",
                name, fixture.getRecords().size(), maximum / 1_000_000.0);
        if (maximum > TARGET_NANOS) {
            throw new AssertionError(name + " exceeded the NFR-04 target");
        }
    }
}
