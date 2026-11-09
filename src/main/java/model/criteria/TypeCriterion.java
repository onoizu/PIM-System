package model.criteria;

import model.exceptions.ValidationException;
import model.records.PIR;
import model.records.RecordType;

public final class TypeCriterion implements Criterion {
    private final RecordType expectedType;

    public TypeCriterion(RecordType expectedType) throws ValidationException {
        if (expectedType == null) {
            throw new ValidationException("A record type is required.");
        }
        this.expectedType = expectedType;
    }

    @Override
    public boolean matches(PIR record) {
        return record.getType() == expectedType;
    }
}
