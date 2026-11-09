package model;

import java.time.LocalDateTime;
import java.util.Map;
import model.exceptions.ValidationException;
import model.records.Contact;
import model.records.Event;
import model.records.FieldName;
import model.records.Note;
import model.records.PIR;
import model.records.RecordTime;
import model.records.RecordType;
import model.records.Task;

/** Builds immutable records from the field values supplied by the caller. */
public final class RecordFactory {
    private RecordFactory() {
    }

    // Create a record  after checking that every required field is supplied
    public static PIR create(long id, RecordType type, Map<FieldName, String> fields)
            throws ValidationException {
        if (type == null) {
            throw new ValidationException("Record type is required.");
        }
        validateFields(type, fields);
        for (FieldName field : type.fields()) {
            if (!fields.containsKey(field)) {
                throw new ValidationException("Missing field '" + field.commandName() + "'.");
            }
        }
        return buildRecord(id, type, fields, null);
    }

    // Build a replacement with the same ID and type, keeping any omitted fields
    public static PIR updated(PIR original, Map<FieldName, String> changes)
            throws ValidationException {
        if (original == null) {
            throw new ValidationException("A record is required for an update.");
        }
        RecordType type = original.getType();
        validateFields(type, changes);
        if (changes.isEmpty()) {
            throw new ValidationException("An update requires at least one field.");
        }
        return buildRecord(original.getId(), type, changes, original);
    }

    // Check which fields were supplied. The constructors validate the completed record.
    private static void validateFields(RecordType type, Map<FieldName, String> fields)
            throws ValidationException {
        if (fields == null) {
            throw new ValidationException("Record fields are required.");
        }
        for (Map.Entry<FieldName, String> entry : fields.entrySet()) {
            FieldName field = entry.getKey();
            if (field == null) {
                throw new ValidationException("Field names are required.");
            }
            if (!type.fields().contains(field)) {
                throw new ValidationException("Field '" + field.commandName()
                        + "' does not apply to " + type.commandName() + " records.");
            }
            if (entry.getValue() == null) {
                throw new ValidationException("A value is required for field '"
                        + field.commandName() + "'.");
            }
        }
    }

    // Use the same constructors for new and updated records.
    private static PIR buildRecord(long id, RecordType type, Map<FieldName, String> fields,
                                   PIR original) throws ValidationException {
        return switch (type) {
            case NOTE -> new Note(id, textValue(fields, original, FieldName.TEXT));
            case TASK -> new Task(id,
                    textValue(fields, original, FieldName.DESCRIPTION),
                    timeValue(fields, original, FieldName.DEADLINE));
            case EVENT -> new Event(id,
                    textValue(fields, original, FieldName.DESCRIPTION),
                    timeValue(fields, original, FieldName.START),
                    timeValue(fields, original, FieldName.ALARM));
            case CONTACT -> new Contact(id,
                    textValue(fields, original, FieldName.NAME),
                    textValue(fields, original, FieldName.ADDRESS),
                    textValue(fields, original, FieldName.MOBILE));
        };
    }

    // Creation supplies every field, Updates use the original for omitted fields
    private static String textValue(Map<FieldName, String> fields, PIR original, FieldName field) {
        if (fields.containsKey(field)) {
            return fields.get(field);
        }
        return original.getTextField(field).orElseThrow();
    }

    private static LocalDateTime timeValue(Map<FieldName, String> fields, PIR original,
                                          FieldName field) throws ValidationException {
        if (fields.containsKey(field)) {
            try {
                return RecordTime.parse(fields.get(field));
            } catch (ValidationException error) {
                // Name the field so the user knows which time to fix.
                throw new ValidationException(field.commandName() + ": " + error.getMessage(), error);
            }
        }
        return original.getTimeField(field).orElseThrow();
    }
}
