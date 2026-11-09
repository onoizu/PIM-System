package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import model.criteria.Criterion;
import model.exceptions.RecordNotFoundException;
import model.exceptions.ValidationException;
import model.records.FieldName;
import model.records.PIR;
import model.records.RecordType;

/** Keeps the active records and allocates their IDs. */
public final class RecordBook {
    private static final Comparator<PIR> BY_ID = Comparator.comparingLong(PIR::getId);

    private Map<Long, PIR> recordsById = new HashMap<>();
    private long nextId = 1;

    public PIR create(RecordType type, Map<FieldName, String> fields)
            throws ValidationException {
        if (nextId == Long.MAX_VALUE) {
            throw new ValidationException("No more record IDs are available.");
        }
        // A rejected record does not use up an ID.
        PIR record = RecordFactory.create(nextId, type, fields);
        recordsById.put(nextId, record);
        nextId++;
        return record;
    }

    public PIR get(long id) throws RecordNotFoundException {
        PIR record = recordsById.get(id);
        if (record == null) {
            throw new RecordNotFoundException("Record not found: " + id);
        }
        return record;
    }

    public List<PIR> getAll() {
        List<PIR> records = new ArrayList<>(recordsById.values());
        records.sort(BY_ID);
        return Collections.unmodifiableList(records);
    }

    public PIR update(long id, Map<FieldName, String> changes)
            throws RecordNotFoundException, ValidationException {
        // Build and validate the replacement before changing the book.
        PIR replacement = RecordFactory.updated(get(id), changes);
        recordsById.put(id, replacement);
        return replacement;
    }

    public void delete(long id) throws RecordNotFoundException {
        if (recordsById.remove(id) == null) {
            throw new RecordNotFoundException("Record not found: " + id);
        }
    }

    public List<PIR> search(Criterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        List<PIR> matches = new ArrayList<>();
        for (PIR record : recordsById.values()) {
            if (criterion.matches(record)) {
                matches.add(record);
            }
        }
        matches.sort(BY_ID);
        return Collections.unmodifiableList(matches);
    }

    public RecordSnapshot snapshot() throws ValidationException {
        return new RecordSnapshot(new ArrayList<>(recordsById.values()), nextId);
    }

    public void replaceState(RecordSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<Long, PIR> replacement = new HashMap<>();
        for (PIR record : snapshot.getRecords()) {
            replacement.put(record.getId(), record);
        }
        recordsById = replacement;
        nextId = snapshot.getNextId();
    }
}
