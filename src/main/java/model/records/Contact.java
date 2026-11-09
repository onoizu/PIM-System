package model.records;

import java.util.Optional;
import model.exceptions.ValidationException;

public final class Contact extends PIR {
    private final String name;
    private final String address;
    private final String mobile;

    public Contact(long id, String name, String address, String mobile) throws ValidationException {
        super(id, RecordType.CONTACT);
        this.name = requireText(FieldName.NAME, name);
        this.address = requireText(FieldName.ADDRESS, address);
        this.mobile = requireText(FieldName.MOBILE, mobile);
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getMobile() {
        return mobile;
    }

    @Override
    public Optional<String> getTextField(FieldName field) {
        if (field == null) {
            return Optional.empty();
        }
        return switch (field) {
            case NAME -> Optional.of(name);
            case ADDRESS -> Optional.of(address);
            case MOBILE -> Optional.of(mobile);
            default -> Optional.empty();
        };
    }
}
