package tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import controller.CommandParseException;
import controller.CommandParser;
import controller.PimController;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import model.RecordBook;
import model.RecordSnapshot;
import model.criteria.CriterionParser;
import model.persistence.PimFileStorage;
import model.records.Event;
import model.records.FieldName;
import model.records.Note;
import view.ConsoleView;
import org.junit.Test;

public final class ConsoleTests {
    // Command syntax preserves quoted fields and rejects malformed assignments.
    @Test
    public void commandSyntax() throws Exception {
        CommandParser parser = new CommandParser();
        assertEquals("type = note && text contains \"x\"", parser.parse("  search type = note && text contains \"x\"").getBody());
        assertEquals(Map.of(FieldName.TEXT, " a \"quote\" \\ path "), parser.parseFields("text=\" a \\\"quote\\\" \\\\ path \""));
        for (String invalid : List.of("", "text=\"a\" text=\"b\"", "text=\"x", "text=\"a\"name=\"b\"",
                "text=\"\\t\"", "text=\"abc\\", "text =\"x\"", "id=\"2\"", "TEXT=\"x\"")) {
            assertThrows(CommandParseException.class, () -> parser.parseFields(invalid));
        }
        assertThrows(CommandParseException.class, () -> parser.parse("unknown"));
    }

    // Event 23 search, update, rejected update and save/load round trip.
    @Test
    public void eventScenario() throws Exception {
        Path directory = Files.createTempDirectory("pim-console-event-");
        Path initial = directory.resolve("initial event.pim");
        Path saved = directory.resolve("saved event.pim");
        try {
            PimFileStorage storage = new PimFileStorage();
            storage.save(initial, new RecordSnapshot(List.of(new Note(3, "Unrelated note"),
                    new Event(23, "COMP3211 project consultation", LocalDateTime.of(2026, 11, 16, 14, 0),
                            LocalDateTime.of(2026, 11, 16, 13, 30))), 24));
            RecordBook book = new RecordBook();
            String transcript = run(book, """
                    load "%s"
                    search type = event && description contains "consultation" && (start < "2026-11-18 00:00" || alarm = "2026-11-16 13:30")
                    update 23 start="2026-11-18 16:00" alarm="2026-11-18 15:30"
                    print 23
                    search type = event && (start < "2026-11-18 00:00" || alarm = "2026-11-16 13:30")
                    update 23 description="must not commit" alarm="2026-11-18 16:30"
                    save "%s"
                    update 23 description="temporary local edit"
                    load "%s"
                    yes
                    print 23
                    exit
                    """.formatted(commandPath(initial), commandPath(saved), commandPath(saved)));
            contains(transcript, "1 matching record:", "start: 2026-11-16 14:00", "No matching records.",
                    "Updated record 23. Unsaved changes.", "Error:", "Discard unsaved changes and load?",
                    "description: COMP3211 project consultation", "start: 2026-11-18 16:00",
                    "alarm: 2026-11-18 15:30");
            Event event = (Event) book.get(23);
            assertEquals("COMP3211 project consultation", event.getDescription());
            assertEquals(LocalDateTime.of(2026, 11, 18, 16, 0), event.getStart());
            assertEquals(LocalDateTime.of(2026, 11, 18, 15, 30), event.getAlarm());
            assertEquals(24L, book.snapshot().getNextId());
            assertEquals(24L, storage.load(saved).getNextId());

            RecordBook fresh = new RecordBook();
            String restored = run(fresh, """
                    load "%s"
                    print all
                    create note text="New after reload"
                    exit
                    y
                    """.formatted(commandPath(saved)));
            contains(restored, "2 matching records:", "Created record 24.");
            assertEquals(event.getStart(), ((Event) fresh.get(23)).getStart());
        } finally {
            Files.deleteIfExists(saved);
            Files.deleteIfExists(initial);
            Files.delete(directory);
        }
    }

    // Overwrite, load and exit refusals keep the unsaved session.
    @Test
    public void cancelConfirmations() throws Exception {
        Path path = Files.createTempFile("pim-console-cancel-", ".pim");
        try {
            new PimFileStorage().save(path, new RecordSnapshot(List.of(new Note(1, "Saved")), 2));
            String original = Files.readString(path);
            RecordBook book = new RecordBook();
            String transcript = run(book, """
                    load "%s"
                    update 1 text="Saved"
                    save "%s"
                    no
                    load "%s"
                    no
                    exit
                    no
                    print 1
                    exit
                    YES
                    """.formatted(commandPath(path), commandPath(path), commandPath(path)));
            contains(transcript, "Overwrite the existing file?", "Save cancelled.",
                    "Discard unsaved changes and load?", "Load cancelled.", "Exit cancelled.", "text: Saved");
            assertEquals(original, Files.readString(path));
            assertEquals("Saved", ((Note) book.get(1)).getText());
            assertEquals(2L, book.snapshot().getNextId());
        } finally {
            Files.delete(path);
        }
    }

    // A bad later file record leaves the active collection and dirty state intact.
    @Test
    public void failedLoad() throws Exception {
        Path path = Files.createTempFile("pim-console-invalid-", ".pim");
        try {
            String invalid = "PIM\t1\nMETA\t10\t2\nNOTE\t5\tYWJj\nEVENT\t6\tINVALID\n";
            Files.writeString(path, invalid);
            RecordBook book = new RecordBook();
            String transcript = run(book, """
                    create note text="Keep me"
                    load "%s"
                    yes
                    print 1
                    exit
                    yes
                    """.formatted(commandPath(path)));
            contains(transcript, "Error:", "line 4", "text: Keep me", "Exit and discard unsaved changes?");
            assertEquals(2L, book.snapshot().getNextId());
            assertEquals(invalid, Files.readString(path));
        } finally {
            Files.delete(path);
        }
    }

    // Save failure leaves a dirty collection available.
    @Test
    public void failedSave() throws Exception {
        Path directory = Files.createTempDirectory("pim-console-missing-parent-");
        try {
            Path target = directory.resolve("missing").resolve("data.pim");
            RecordBook book = new RecordBook();
            String transcript = run(book, """
                    create note text="Keep me"
                    save "%s"
                    print 1
                    exit
                    yes
                    """.formatted(commandPath(target)));
            contains(transcript, "Error:", "text: Keep me", "Exit and discard unsaved changes?");
            assertFalse("A failed save created the target", Files.exists(target));
            assertEquals(2L, book.snapshot().getNextId());
        } finally {
            Files.delete(directory);
        }
    }

    // EOF at confirmation cancels and warns without another prompt.
    @Test
    public void confirmationEof() throws Exception {
        RecordBook book = new RecordBook();
        String transcript = run(book, "create note text=\"Keep me\"\nexit\n");
        contains(transcript, "Exit cancelled.", "Input ended with unsaved changes.");
        assertEquals(2, transcript.split("pim> ", -1).length - 1);
        assertEquals("Keep me", ((Note) book.get(1)).getText());
    }

    // CLI record commands and help topics work in the same session.
    @Test
    public void createPrintDelete() {
        RecordBook book = new RecordBook();
        String transcript = run(book, """
                create note text="Quick note"
                create task description="Submit report" deadline="2026-11-20 20:00"
                create event description="Consultation" start="2026-11-18 16:00" alarm="2026-11-18 15:30"
                create contact name="Alex" address="Room 44" mobile="+00123"
                print all
                delete 2
                print 2
                print 18446744073709551615
                print 3 extra
                print <1>
                print 1
                help
                help search
                help search extra
                exit
                yes
                """);
        contains(transcript, "4 matching records:", "type: note", "type: task", "type: event", "type: contact",
                "mobile: +00123", "Deleted record 2.", "Error: Record not found: 2", "Commands",
                "Error: The record ID is too large. Use print <id> or print all. Example: print 1.",
                "Error: Expected one positive decimal record ID. Use print <id> or print all. Example: print 1.",
                "pim>   id: 1\n  type: note\n  text: Quick note");
        contains(transcript, "  help search", "Find records with search <expression>",
                "Type help search for supported fields and more search examples.",
                "Record types: note, task, event, contact.",
                "Text fields: text, description, name, address, mobile.",
                "Time fields: deadline, start, alarm.",
                "search type = note && text contains \"project\"",
                "search type = task && deadline < \"2026-11-20 20:00\"",
                "search type = event && !(description contains \"cancelled\")",
                "Error: Use help or help search.");
        assertEquals(List.of(1L, 3L, 4L), book.getAll().stream().map(record -> record.getId()).toList());
    }

    // Escape filesystem paths for the CLI quoted-value grammar on all platforms.
    private static String commandPath(Path path) {
        return path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String run(RecordBook book, String commands) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            BufferedReader input = new BufferedReader(new StringReader(commands));
            new PimController(book, new CriterionParser(), new PimFileStorage(), new CommandParser(),
                    new ConsoleView(input, output)).run();
        }
        return bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void contains(String transcript, String... fragments) {
        for (String fragment : fragments) {
            assertTrue("Missing output <" + fragment + "> in:\n" + transcript, transcript.contains(fragment));
        }
    }
}
