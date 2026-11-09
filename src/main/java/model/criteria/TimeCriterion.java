package model.criteria;

import java.time.LocalDateTime;
import model.exceptions.ValidationException;
import model.records.FieldName;
import model.records.PIR;
import model.records.RecordTime;

public final class TimeCriterion implements Criterion {
    private final FieldName field;
    private final TimeOperator operator;
    private final LocalDateTime reference;

    public TimeCriterion(FieldName field, TimeOperator operator, LocalDateTime reference)
            throws ValidationException {
        if (field == null || !field.isTime()) {
            throw new ValidationException("A time criterion requires a time field.");
        }
        if (operator == null) {
            throw new ValidationException("A time comparison operator is required.");
        }
        this.field = field;
        this.operator = operator;
        this.reference = RecordTime.requireMinute(reference);
    }

    @Override
    public boolean matches(PIR record) {
        LocalDateTime actual = record.getTimeField(field).orElse(null);
        if (actual == null) {
            return false;
        }
        return switch (operator) {
            case BEFORE -> actual.isBefore(reference);
            case AFTER -> actual.isAfter(reference);
            case EQUAL -> actual.equals(reference);
        };
    }
}
