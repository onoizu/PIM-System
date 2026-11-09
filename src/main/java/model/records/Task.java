package model.records;

import java.time.LocalDateTime;
import java.util.Optional;
import model.exceptions.ValidationException;

public final class Task extends PIR {
    private final String description;
    private final LocalDateTime deadline;

    public Task(long id, String description, LocalDateTime deadline) throws ValidationException {
        super(id, RecordType.TASK);
        this.description = requireText(FieldName.DESCRIPTION, description);
        this.deadline = RecordTime.requireMinute(deadline);
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    @Override
    public Optional<String> getTextField(FieldName field) {
        return field == FieldName.DESCRIPTION ? Optional.of(description) : Optional.empty();
    }

    @Override
    public Optional<LocalDateTime> getTimeField(FieldName field) {
        return field == FieldName.DEADLINE ? Optional.of(deadline) : Optional.empty();
    }
}
