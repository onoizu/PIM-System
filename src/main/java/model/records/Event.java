package model.records;

import java.time.LocalDateTime;
import java.util.Optional;
import model.exceptions.ValidationException;

public final class Event extends PIR {
    private final String description;
    private final LocalDateTime start;
    private final LocalDateTime alarm;

    public Event(long id, String description, LocalDateTime start, LocalDateTime alarm)
            throws ValidationException {
        super(id, RecordType.EVENT);
        this.description = requireText(FieldName.DESCRIPTION, description);
        this.start = RecordTime.requireMinute(start);
        this.alarm = RecordTime.requireMinute(alarm);
        if (this.alarm.isAfter(this.start)) {
            throw new ValidationException("The alarm must be at or before the event start.");
        }
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getAlarm() {
        return alarm;
    }

    @Override
    public Optional<String> getTextField(FieldName field) {
        return field == FieldName.DESCRIPTION ? Optional.of(description) : Optional.empty();
    }

    @Override
    public Optional<LocalDateTime> getTimeField(FieldName field) {
        if (field == FieldName.START) {
            return Optional.of(start);
        }
        if (field == FieldName.ALARM) {
            return Optional.of(alarm);
        }
        return Optional.empty();
    }
}
