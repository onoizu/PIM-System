package model.records;

import java.util.Optional;
import model.exceptions.ValidationException;

/** An immutable plain-text note that keeps the user's spacing. */
public final class Note extends PIR {
    private final String text;

    public Note(long id, String text) throws ValidationException {
        super(id, RecordType.NOTE);
        this.text = requireText(FieldName.TEXT, text);
    }

    public String getText() {
        return text;
    }

    @Override
    public Optional<String> getTextField(FieldName field) {
        return field == FieldName.TEXT ? Optional.of(text) : Optional.empty();
    }
}
