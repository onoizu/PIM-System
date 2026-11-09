package model.criteria;

import model.exceptions.ValidationException;
import model.records.PIR;

public final class AndCriterion implements Criterion {
    private final Criterion left;
    private final Criterion right;

    public AndCriterion(Criterion left, Criterion right) throws ValidationException {
        if (left == null || right == null) {
            throw new ValidationException("AND requires two criteria.");
        }
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean matches(PIR record) {
        return left.matches(record) && right.matches(record);
    }
}
