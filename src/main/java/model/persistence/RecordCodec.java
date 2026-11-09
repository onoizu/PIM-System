package model.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import model.RecordFactory;
import model.exceptions.ValidationException;
import model.records.FieldName;
import model.records.PIR;
import model.records.RecordTime;
import model.records.RecordType;

/** Converts between records and the tab-separated version 1 record layout. */
public final class RecordCodec {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private RecordCodec() {
    }

    public static String encodeRecord(PIR record) {
        RecordType type = record.getType();
        StringBuilder line = new StringBuilder(type.name());
        line.append('\t').append(record.getId());
        for (FieldName field : type.fields()) {
            line.append('\t');
            if (field.isText()) {
                String text = record.getTextField(field).orElseThrow();
                line.append(ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8)));
            } else {
                line.append(RecordTime.format(record.getTimeField(field).orElseThrow()));
            }
        }
        return line.toString();
    }

    public static PIR decodeRecord(String line) throws ValidationException {
        if (line == null) {
            throw new ValidationException("Missing record line.");
        }
        String[] columns = line.split("\t", -1);
        RecordType type;
        try {
            type = RecordType.valueOf(columns[0]);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("Unknown record tag.", exception);
        }

        List<FieldName> fields = type.fields();
        if (columns.length != fields.size() + 2) {
            throw new ValidationException("Incorrect number of columns for " + type.name() + ".");
        }

        long id = parseDecimal(columns[1], "Record ID");
        Map<FieldName, String> values = new EnumMap<>(FieldName.class);
        for (int index = 0; index < fields.size(); index++) {
            FieldName field = fields.get(index);
            String value = columns[index + 2];
            values.put(field, field.isText() ? decodeText(value, field) : value);
        }
        return RecordFactory.create(id, type, values);
    }

    static long parseDecimal(String value, String label) throws ValidationException {
        if (value.isEmpty()) {
            throw new ValidationException(label + " must be a decimal integer.");
        }
        for (int index = 0; index < value.length(); index++) {
            char digit = value.charAt(index);
            if (digit < '0' || digit > '9') {
                throw new ValidationException(label + " must be a decimal integer.");
            }
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ValidationException(label + " is out of range.", exception);
        }
    }

    private static String decodeText(String value, FieldName field) throws ValidationException {
        try {
            byte[] bytes = DECODER.decode(value);
            if (!ENCODER.encodeToString(bytes).equals(value)) {
                throw new IllegalArgumentException("Expected standard padded Base64.");
            }
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw new ValidationException("Invalid Base64 or UTF-8 in " + field.commandName() + ".",
                    exception);
        }
    }
}
