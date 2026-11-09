package model.criteria;

import model.records.PIR;

@FunctionalInterface
public interface Criterion {
    boolean matches(PIR record);
}
