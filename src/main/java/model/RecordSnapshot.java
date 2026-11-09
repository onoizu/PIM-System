package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import model.exceptions.ValidationException;
import model.records.PIR;

/** A validated, read-only collection state for saving and loading. */
public final class RecordSnapshot {
    private final List<PIR> records;
    // Keep the allocation history even if the highest record ID was deleted.
    private final long nextId;

    /** Validate the IDs and take a copy of the list. */
    public RecordSnapshot(List<PIR> records, long nextId) throws ValidationException {
        if (records == null) {
            throw new ValidationException("Snapshot records are required.");
        }
        if (nextId <= 0) {
            throw new ValidationException("The next record ID must be positive.");
        }

        // The records are immutable, so only the list needs to be copied.
        List<PIR> copy = new ArrayList<>(records.size());
        Set<Long> seenIds = new HashSet<>();
        for (PIR record : records) {
            if (record == null) {
                throw new ValidationException("A snapshot cannot contain a null record.");
            }
            long id = record.getId();
            // Gaps are allowed, but the next ID must be above every stored ID.
            if (id <= 0 || id >= nextId) {
                throw new ValidationException("Snapshot record ID " + id
                        + " must be positive and less than the next record ID.");
            }
            if (!seenIds.add(id)) {
                throw new ValidationException("Duplicate snapshot record ID: " + id);
            }
            copy.add(record);
        }
        this.records = Collections.unmodifiableList(copy);
        this.nextId = nextId;
    }

    public List<PIR> getRecords() {
        return records;
    }

    public long getNextId() {
        return nextId;
    }

}
