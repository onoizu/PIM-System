package model.criteria;

import model.exceptions.ValidationException;
import model.records.FieldName;
import model.records.PIR;

public final class TextCriterion implements Criterion {
    private final FieldName field;
    private final String fragment;

    public TextCriterion(FieldName field, String fragment) throws ValidationException {
        if (field == null || !field.isText()) {
            throw new ValidationException("A text criterion requires a text field.");
        }
        if (fragment == null || fragment.isEmpty()) {
            throw new ValidationException("The search text must not be empty.");
        }
        this.field = field;
        this.fragment = fragment;
    }

    @Override
    public boolean matches(PIR record) {
        String text = record.getTextField(field).orElse(null);
        return text != null && text.contains(fragment);
    }
}
