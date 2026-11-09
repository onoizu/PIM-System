package tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import model.RecordBook;
import model.RecordSnapshot;
import model.criteria.TypeCriterion;
import model.exceptions.RecordNotFoundException;
import model.exceptions.ValidationException;
import model.records.Contact;
import model.records.Event;
import model.records.FieldName;
import model.records.Note;
import model.records.PIR;
import model.records.RecordTime;
import model.records.RecordType;
import model.records.Task;
import org.junit.Test;

public final class RecordTests {
    // US2-US5: create records with complete, typed fields.
    @Test
    public void createRecords() throws Exception {
        RecordBook book = new RecordBook();
        String originalText = "  Café\t\n\ud83d\ude00  ";
        Note note = (Note) book.create(RecordType.NOTE, Map.of(FieldName.TEXT, originalText));
        Task task = (Task) book.create(RecordType.TASK, Map.of(
                FieldName.DESCRIPTION, "Finish report", FieldName.DEADLINE, "2026-11-20 20:00"));
        Event event = (Event) book.create(RecordType.EVENT, Map.of(
                FieldName.DESCRIPTION, "Project consultation",
                FieldName.START, "2026-11-16 14:00", FieldName.ALARM, "2026-11-16 14:00"));
        Contact contact = (Contact) book.create(RecordType.CONTACT, Map.of(
                FieldName.NAME, "Casey", FieldName.ADDRESS, "Room 03", FieldName.MOBILE, "+001234"));

        assertEquals(originalText, note.getText());
        assertEquals(originalText, note.getTextField(FieldName.TEXT).orElseThrow());
        assertTrue("A note has no deadline", note.getTimeField(FieldName.DEADLINE).isEmpty());
        assertEquals("Finish report", task.getDescription());
        assertEquals(task.getDescription(), task.getTextField(FieldName.DESCRIPTION).orElseThrow());
        assertEquals(LocalDateTime.of(2026, 11, 20, 20, 0), task.getDeadline());
        assertEquals(task.getDeadline(), task.getTimeField(FieldName.DEADLINE).orElseThrow());
        assertEquals("Project consultation", event.getDescription());
        assertEquals(event.getDescription(), event.getTextField(FieldName.DESCRIPTION).orElseThrow());
        assertEquals(event.getStart(), event.getAlarm());
        assertEquals("Casey", contact.getName());
        assertEquals("Room 03", contact.getAddress());
        assertEquals("+001234", contact.getMobile());
        assertEquals(contact.getName(), contact.getTextField(FieldName.NAME).orElseThrow());
        assertEquals(contact.getAddress(), contact.getTextField(FieldName.ADDRESS).orElseThrow());
        assertEquals(contact.getMobile(), contact.getTextField(FieldName.MOBILE).orElseThrow());
        assertEquals(List.of(note, task, event, contact), book.getAll());
    }

    // F1: rejected creation leaves the next ID available.
    @Test
    public void invalidCreation() throws Exception {
        RecordBook book = new RecordBook();
        assertThrows(ValidationException.class, () -> book.create(RecordType.NOTE, Map.of()));
        assertThrows(ValidationException.class, () -> book.create(RecordType.NOTE,
                Map.of(FieldName.TEXT, "Valid note", FieldName.NAME, "Extra field")));
        assertThrows(ValidationException.class, () -> book.create(RecordType.NOTE,
                Map.of(FieldName.TEXT, " \t")));
        assertThrows(ValidationException.class, () -> book.create(RecordType.TASK,
                Map.of(FieldName.DESCRIPTION, "Task", FieldName.DEADLINE, "2026-02-29 09:00")));
        assertEquals(List.of(), book.getAll());
        assertEquals(1L, book.snapshot().getNextId());
        assertEquals(1L, book.create(RecordType.NOTE, Map.of(FieldName.TEXT, "Valid note")).getId());
    }

    // Record constructors enforce domain invariants.
    @Test
    public void invalidRecords() {
        LocalDateTime minute = LocalDateTime.of(2026, 11, 16, 14, 0);
        assertThrows(ValidationException.class, () -> new Note(0, "Text"));
        assertThrows(ValidationException.class, () -> new Note(1, null));
        assertThrows(ValidationException.class, () -> new Note(1, "\ud800"));
        assertThrows(ValidationException.class, () -> new Note(1, "Invalid \udc00"));
        assertThrows(ValidationException.class, () -> new Task(1, "Task", null));
        assertThrows(ValidationException.class, () -> new Task(1, "Task", minute.plusSeconds(1)));
        assertThrows(ValidationException.class, () -> new Event(1, "Event", minute, minute.plusMinutes(1)));
        assertThrows(ValidationException.class, () -> new Contact(1, "Casey", "Room 03", " "));
    }

    // Times use strict minute precision.
    @Test
    public void strictTimes() throws Exception {
        assertEquals(LocalDateTime.of(2024, 2, 29, 9, 5), RecordTime.parse("2024-02-29 09:05"));
        assertEquals("0000-02-29 00:00", RecordTime.format(RecordTime.parse("0000-02-29 00:00")));
        assertEquals("9999-12-31 23:59", RecordTime.format(RecordTime.parse("9999-12-31 23:59")));
        assertThrows(ValidationException.class, () -> RecordTime.parse("2026-2-01 09:00"));
        assertThrows(ValidationException.class, () -> RecordTime.parse("2026-02-01 24:00"));
        assertThrows(ValidationException.class, () -> RecordTime.parse("2026-02-01 09:00:01"));
        assertThrows(ValidationException.class, () -> RecordTime.parse("2026-02-01 09:00Z"));
        assertThrows(ValidationException.class,
                () -> RecordTime.requireMinute(LocalDateTime.of(10000, 1, 1, 0, 0)));
        assertThrows(ValidationException.class,
                () -> RecordTime.requireMinute(LocalDateTime.of(2026, 2, 1, 9, 0).plusNanos(1)));
    }

    // US6: validate and commit a combined event update.
    @Test
    public void updateEvent() throws Exception {
        RecordBook book = new RecordBook();
        Event original = createEvent(book);
        Event updated = (Event) book.update(original.getId(), Map.of(
                FieldName.START, "2026-11-16 12:00", FieldName.ALARM, "2026-11-16 11:30"));

        assertEquals(original.getId(), updated.getId());
        assertEquals(original.getDescription(), updated.getDescription());
        assertEquals(LocalDateTime.of(2026, 11, 16, 12, 0), updated.getStart());
        assertEquals(LocalDateTime.of(2026, 11, 16, 11, 30), updated.getAlarm());
        assertEquals(LocalDateTime.of(2026, 11, 16, 14, 0), original.getStart());
        assertEquals(LocalDateTime.of(2026, 11, 16, 13, 30), original.getAlarm());
        assertEquals(updated, book.get(updated.getId()));
        assertEquals(2L, book.snapshot().getNextId());
    }

    // F2: a rejected update preserves the entire event.
    @Test
    public void invalidUpdate() throws Exception {
        RecordBook book = new RecordBook();
        Event original = createEvent(book);
        long id = original.getId();
        Map<FieldName, String> nullValue = new EnumMap<>(FieldName.class);
        nullValue.put(FieldName.DESCRIPTION, null);
        assertThrows(ValidationException.class, () -> book.update(id, Map.of(
                FieldName.DESCRIPTION, "Tentative change", FieldName.START, "2026-11-16 12:00")));
        assertThrows(ValidationException.class, () -> book.update(id, Map.of(FieldName.TEXT, "Wrong field")));
        assertThrows(ValidationException.class, () -> book.update(id, Map.of()));
        assertThrows(ValidationException.class, () -> book.update(id, nullValue));
        assertThrows(ValidationException.class, () -> book.update(id, null));
        assertEquals(List.of(original), book.getAll());
        assertEquals(2L, book.snapshot().getNextId());
    }

    // US7/US8: return ordered, immutable search and print results.
    @Test
    public void orderedResults() throws Exception {
        RecordBook book = new RecordBook();
        Note first = new Note(3, "First");
        Note last = new Note(32, "Last");
        Task middle = new Task(18, "Middle", LocalDateTime.of(2026, 11, 16, 15, 0));
        book.replaceState(new RecordSnapshot(List.of(last, first, middle), 40));
        List<PIR> ordered = book.getAll();
        List<PIR> matches = book.search(new TypeCriterion(RecordType.NOTE));
        assertEquals(List.of(first, middle, last), ordered);
        assertEquals(List.of(first, last), matches);
        assertThrows(UnsupportedOperationException.class, ordered::clear);
        assertThrows(UnsupportedOperationException.class, matches::clear);
        assertEquals(List.of(first, middle, last), book.getAll());
    }

    // US9: delete only the selected ID and never reuse it.
    @Test
    public void deleteRecord() throws Exception {
        RecordBook book = new RecordBook();
        PIR first = book.create(RecordType.NOTE, Map.of(FieldName.TEXT, "First"));
        PIR second = book.create(RecordType.NOTE, Map.of(FieldName.TEXT, "Second"));
        book.delete(first.getId());
        assertThrows(RecordNotFoundException.class, () -> book.get(first.getId()));
        assertEquals(List.of(second), book.getAll());
        assertEquals(3L, book.create(RecordType.NOTE, Map.of(FieldName.TEXT, "Third")).getId());
    }

    // Invalid or unknown IDs cannot modify the book.
    @Test
    public void unknownIds() throws Exception {
        RecordBook book = new RecordBook();
        Event record = createEvent(book);
        assertThrows(RecordNotFoundException.class, () -> book.get(0));
        assertThrows(RecordNotFoundException.class, () -> book.delete(-1));
        assertThrows(RecordNotFoundException.class, () -> book.delete(500));
        assertThrows(RecordNotFoundException.class, () -> book.update(500,
                Map.of(FieldName.DESCRIPTION, "Absent record")));
        assertEquals(List.of(record), book.getAll());
        assertEquals(2L, book.snapshot().getNextId());
    }

    // An exhausted ID counter cannot overflow.
    @Test
    public void exhaustedIds() throws Exception {
        RecordBook book = new RecordBook();
        book.replaceState(new RecordSnapshot(List.of(), Long.MAX_VALUE - 1));
        PIR last = book.create(RecordType.NOTE, Map.of(FieldName.TEXT, "Last"));
        assertEquals(Long.MAX_VALUE - 1, last.getId());
        assertThrows(ValidationException.class,
                () -> book.create(RecordType.NOTE, Map.of(FieldName.TEXT, "Overflow")));
        assertEquals(Long.MAX_VALUE, book.snapshot().getNextId());
        assertEquals(List.of(last), book.getAll());
    }

    // A snapshot owns its list and restores the ID counter.
    @Test
    public void snapshotIsolation() throws Exception {
        Note original = new Note(3, "Saved note");
        List<PIR> source = new ArrayList<>(List.of(original));
        RecordSnapshot snapshot = new RecordSnapshot(source, 7);
        source.clear();
        assertEquals(List.of(original), snapshot.getRecords());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getRecords().add(original));

        RecordBook book = new RecordBook();
        book.replaceState(snapshot);
        book.update(original.getId(), Map.of(FieldName.TEXT, "Edited note"));
        assertEquals("Saved note", snapshot.getRecords().get(0).getTextField(FieldName.TEXT).orElseThrow());
        book.replaceState(snapshot);
        assertEquals(List.of(original), book.getAll());
        assertEquals(7L, book.create(RecordType.NOTE, Map.of(FieldName.TEXT, "New note")).getId());
    }

    // Reject inconsistent snapshots.
    @Test
    public void invalidSnapshots() throws Exception {
        Note note = new Note(3, "Saved note");
        List<PIR> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(ValidationException.class, () -> new RecordSnapshot(null, 1));
        assertThrows(ValidationException.class, () -> new RecordSnapshot(withNull, 4));
        assertThrows(ValidationException.class, () -> new RecordSnapshot(List.of(), 0));
        assertThrows(ValidationException.class, () -> new RecordSnapshot(List.of(note), 3));
        assertThrows(ValidationException.class, () -> new RecordSnapshot(List.of(note, note), 4));
    }

    private static Event createEvent(RecordBook book) throws ValidationException {
        return (Event) book.create(RecordType.EVENT, Map.of(
                FieldName.DESCRIPTION, "COMP3211 project consultation",
                FieldName.START, "2026-11-16 14:00", FieldName.ALARM, "2026-11-16 13:30"));
    }
}
