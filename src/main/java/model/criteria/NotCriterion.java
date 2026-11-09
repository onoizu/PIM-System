package model.criteria;

import model.exceptions.ValidationException;
import model.records.PIR;

public final class NotCriterion implements Criterion {
    private final Criterion operand;

    public NotCriterion(Criterion operand) throws ValidationException {
        if (operand == null) {
            throw new ValidationException("NOT requires a criterion.");
        }
        this.operand = operand;
    }

    @Override
    public boolean matches(PIR record) {
        return !operand.matches(record);
    }
}
