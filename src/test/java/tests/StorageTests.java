package tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import model.RecordBook;
import model.RecordSnapshot;
import model.exceptions.StorageException;
import model.exceptions.ValidationException;
import model.persistence.PimFileStorage;
import model.persistence.RecordCodec;
import model.records.Contact;
import model.records.Event;
import model.records.FieldName;
import model.records.Note;
import model.records.PIR;
import model.records.RecordType;
import model.records.Task;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class StorageTests {
    private static final String HEADER = "PIM\t1\n";
    private static final String NOTE = "NOTE\t2\tbm90ZQ==\n";
    private static final PimFileStorage STORAGE = new PimFileStorage();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    // Storage round trip preserves four record types and nextId.
    @Test
    public void roundTrip() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath();
        Path file = directory.resolve("round trip.pim");
        String noteText = "  first\tline\ncafé \ud83d\udcdd\\  ";
        LocalDateTime deadline = LocalDateTime.of(2026, 11, 18, 12, 0);
        LocalDateTime start = LocalDateTime.of(2026, 11, 18, 16, 0);
        LocalDateTime alarm = LocalDateTime.of(2026, 11, 18, 15, 30);
        RecordSnapshot original = new RecordSnapshot(List.of(
                new Contact(13, "Alex", "  Main Street  ", "+00123"),
                new Event(9, "Consultation", start, alarm),
                new Task(4, "Review", deadline),
                new Note(2, noteText)), 20);

        STORAGE.save(file, original);
        List<String> lines = Files.readAllLines(file);
        assertEquals("PIM\t1", lines.get(0));
        assertEquals("META\t20\t4", lines.get(1));
        assertEquals(List.of("NOTE", "TASK", "EVENT", "CONTACT"),
                lines.subList(2, 6).stream().map(line -> line.substring(0, line.indexOf('\t'))).toList());

        RecordSnapshot restored = STORAGE.load(file);
        List<PIR> records = restored.getRecords();
        assertEquals(List.of(2L, 4L, 9L, 13L), records.stream().map(PIR::getId).toList());
        assertEquals(noteText, ((Note) records.get(0)).getText());
        Task task = (Task) records.get(1);
        assertEquals("Review", task.getDescription());
        assertEquals(deadline, task.getDeadline());
        Event event = (Event) records.get(2);
        assertEquals("Consultation", event.getDescription());
        assertEquals(start, event.getStart());
        assertEquals(alarm, event.getAlarm());
        Contact contact = (Contact) records.get(3);
        assertEquals("Alex", contact.getName());
        assertEquals("  Main Street  ", contact.getAddress());
        assertEquals("+00123", contact.getMobile());
        assertEquals(20L, restored.getNextId());

        RecordBook reopened = new RecordBook();
        reopened.replaceState(restored);
        assertEquals(20L, reopened.create(RecordType.NOTE, Map.of(FieldName.TEXT, "Next")).getId());
    }

    // Storage accepts CRLF and an empty collection.
    @Test
    public void supportedLayouts() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath();
        Path file = directory.resolve("layouts.pim");
        STORAGE.save(file, new RecordSnapshot(List.of(), 9));
        assertEquals(HEADER + "META\t9\t0\n", Files.readString(file));
        assertEquals(List.of(), STORAGE.load(file).getRecords());
        assertEquals(9L, STORAGE.load(file).getNextId());

        Files.writeString(file, "PIM\t1\r\nMETA\t4\t1\r\nNOTE\t2\tbm90ZQ==");
        RecordSnapshot restored = STORAGE.load(file);
        assertEquals("note", ((Note) restored.getRecords().get(0)).getText());
        assertEquals(4L, restored.getNextId());

        STORAGE.save(file, new RecordSnapshot(List.of(), 1));
        assertEquals(HEADER + "META\t1\t0\n", Files.readString(file));
    }

    // Codec rejects malformed record columns and values.
    @Test
    public void badRecordLines() {
        for (String line : List.of(
                "note\t2\tbm90ZQ==",
                "NOTE\t2",
                "NOTE\t2\tbm90ZQ==\t",
                "NOTE\t+2\tbm90ZQ==",
                "NOTE\t0\tbm90ZQ==",
                "NOTE\t9223372036854775808\tbm90ZQ==",
                "NOTE\t2\t?",
                "NOTE\t2\tbm90ZQ",
                "NOTE\t2\t/w==",
                "NOTE\t2\tIA==",
                "TASK\t2\tbm90ZQ==\t2026-02-30 12:00",
                "EVENT\t2\tbm90ZQ==\t2026-11-18 16:00\t2026-11-18 16:30")) {
            assertThrows(ValidationException.class, () -> RecordCodec.decodeRecord(line));
        }
    }

    // Storage rejects bad headers, counts and record IDs.
    @Test
    public void badSnapshotLines() throws IOException {
        Path directory = temporaryFolder.getRoot().toPath();
        Path file = directory.resolve("invalid.pim");
        rejectsAtLine(file, "PIM\t2\nMETA\t1\t0\n", 1);
        rejectsAtLine(file, HEADER, 2);
        rejectsAtLine(file, HEADER + "META\t0\t0\n", 2);
        rejectsAtLine(file, HEADER + "META\t5\t2147483648\n", 2);
        rejectsAtLine(file, HEADER + "META\t5\t0\textra\n", 2);
        rejectsAtLine(file, HEADER + "META\t5\t2\n" + NOTE + "broken\n", 4);
        rejectsAtLine(file, HEADER + "META\t5\t2\n" + NOTE + NOTE, 4);
        rejectsAtLine(file, HEADER + "META\t2\t1\n" + NOTE, 3);
        rejectsAtLine(file, HEADER + "META\t5\t2\n" + NOTE, 4);
        rejectsAtLine(file, HEADER + "META\t5\t0\n" + NOTE, 3);
        rejectsAtLine(file, HEADER + "META\t5\t1\n" + NOTE + "\n", 4);
        rejectsAtLine(file, "PIM\t1\rMETA\t1\t0\r", 1);
    }

    private static void rejectsAtLine(Path file, String content, long line) throws IOException {
        Files.writeString(file, content);
        StorageException failure = assertThrows(StorageException.class, () -> STORAGE.load(file));
        assertTrue("Expected a failure at line " + line + " but got " + failure.getMessage(),
                failure.getMessage().contains("at line " + line + ":"));
        assertEquals(content, Files.readString(file));
    }

    // Storage rejects malformed UTF-8 in the file.
    @Test
    public void malformedFileEncoding() throws IOException {
        Path directory = temporaryFolder.getRoot().toPath();
        Path file = directory.resolve("invalid-encoding.pim");
        byte[] bytes = (HEADER + "META\t5\t1\n" + NOTE + "!").getBytes(StandardCharsets.UTF_8);
        bytes[bytes.length - 1] = (byte) 0xff;
        Files.write(file, bytes);
        StorageException failure = assertThrows(StorageException.class, () -> STORAGE.load(file));
        assertTrue("Expected a UTF-8 diagnostic.", failure.getMessage().contains("UTF-8"));
    }

    // Storage rejects invalid destinations without damaging saved data.
    @Test
    public void failedSave() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath();
        RecordSnapshot snapshot = new RecordSnapshot(List.of(new Note(2, "note")), 5);
        Path file = directory.resolve("original.pim");
        STORAGE.save(file, snapshot);
        String saved = Files.readString(file);
        assertThrows(StorageException.class, () -> STORAGE.save(file, null));
        assertEquals(saved, Files.readString(file));

        Path invalidExtension = directory.resolve("original.PIM");
        assertThrows(StorageException.class, () -> STORAGE.save(invalidExtension, snapshot));
        assertThrows(StorageException.class, () -> STORAGE.load(invalidExtension));
        assertTrue("Invalid-extension save created a file.",
                Files.notExists(invalidExtension) || Files.isSameFile(file, invalidExtension));
        assertEquals(saved, Files.readString(file));

        Path destination = Files.createDirectory(directory.resolve("folder.pim"));
        Path marker = Files.writeString(destination.resolve("marker"), "keep this");
        assertThrows(StorageException.class, () -> STORAGE.save(destination, snapshot));
        assertThrows(StorageException.class, () -> STORAGE.load(destination));
        assertEquals("keep this", Files.readString(marker));

        Path missing = directory.resolve("missing/record.pim");
        assertThrows(StorageException.class, () -> STORAGE.save(missing, snapshot));
        assertThrows(StorageException.class, () -> STORAGE.load(missing));
        assertTrue("Save created an unexpected directory.", Files.notExists(missing.getParent()));
    }
}
