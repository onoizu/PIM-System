package model.persistence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import model.RecordSnapshot;
import model.exceptions.StorageException;
import model.exceptions.ValidationException;
import model.records.PIR;

/** Reads and writes complete snapshots independently of the active record book. */
public final class PimFileStorage {
    private static final String HEADER = "PIM\t1";

    /** Write a complete snapshot before replacing the destination. */
    public void save(Path path, RecordSnapshot snapshot) throws StorageException {
        requirePimExtension(path);
        if (snapshot == null) {
            throw new StorageException("A snapshot is required for saving.");
        }

        Path temporary = null;
        try {
            Path target = path.toAbsolutePath();
            checkSaveTarget(target);
            // Keep the temporary file on the same filesystem for an atomic move when supported.
            temporary = Files.createTempFile(target.getParent(), ".pim-", ".tmp");
            writeSnapshot(temporary, snapshot);
            replaceFile(temporary, target);
        } catch (IOException | SecurityException exception) {
            StorageException failure = new StorageException(
                    "Cannot save '" + path + "': " + exception.getMessage(), exception);
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /** Read and validate a file so the caller can install the snapshot in one step. */
    public RecordSnapshot load(Path path) throws StorageException {
        requirePimExtension(path);
        try {
            if (!Files.readAttributes(path, BasicFileAttributes.class).isRegularFile()) {
                throw new IOException("The source must be a regular file.");
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                if (!HEADER.equals(readLine(reader, path, 1))) {
                    throw invalidFile(path, 1, "Expected the version 1 PIM header.");
                }
                Metadata metadata = readMetadata(reader, path);
                List<PIR> records = readRecords(reader, path, metadata);
                return new RecordSnapshot(records, metadata.nextId());
            }
        } catch (CharacterCodingException exception) {
            throw new StorageException("Invalid UTF-8 in '" + path + "'.", exception);
        } catch (IOException | SecurityException exception) {
            throw new StorageException("Cannot load '" + path + "': " + exception.getMessage(),
                    exception);
        } catch (ValidationException exception) {
            throw new StorageException("Invalid snapshot in '" + path + "': " + exception.getMessage(),
                    exception);
        }
    }

    private static void requirePimExtension(Path path) throws StorageException {
        if (path == null || path.getFileName() == null
                || !path.getFileName().toString().endsWith(".pim")) {
            throw new StorageException("The filename must end with .pim.");
        }
    }

    private static void checkSaveTarget(Path target) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw new IOException("The destination must be a regular file.");
            }
        } catch (NoSuchFileException exception) {
            // A new destination is allowed.
        }
    }

    private static void writeSnapshot(Path path, RecordSnapshot snapshot) throws IOException {
        List<PIR> orderedRecords = new ArrayList<>(snapshot.getRecords());
        orderedRecords.sort(Comparator.comparingLong(PIR::getId));
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(HEADER + "\n");
            writer.write("META\t" + snapshot.getNextId() + "\t" + orderedRecords.size() + "\n");
            for (PIR record : orderedRecords) {
                writer.write(RecordCodec.encodeRecord(record));
                writer.write('\n');
            }
        }
    }

    private static void replaceFile(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Metadata readMetadata(BufferedReader reader, Path path)
            throws IOException, StorageException {
        String line = readLine(reader, path, 2);
        if (line == null) {
            throw invalidFile(path, 2, "Missing metadata.");
        }
        String[] columns = line.split("\t", -1);
        if (columns.length != 3 || !columns[0].equals("META")) {
            throw invalidFile(path, 2, "Expected META, nextId and record count.");
        }
        try {
            long nextId = RecordCodec.parseDecimal(columns[1], "nextId");
            long count = RecordCodec.parseDecimal(columns[2], "Record count");
            if (nextId == 0 || count > Integer.MAX_VALUE) {
                throw new ValidationException("nextId must be positive and record count must fit in an int.");
            }
            return new Metadata(nextId, (int) count);
        } catch (ValidationException exception) {
            throw new StorageException(location(path, 2) + exception.getMessage(), exception);
        }
    }

    private static List<PIR> readRecords(BufferedReader reader, Path path, Metadata metadata)
            throws IOException, StorageException {
        List<PIR> records = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (int index = 0; index < metadata.recordCount(); index++) {
            long lineNumber = (long) index + 3;
            String line = readLine(reader, path, lineNumber);
            if (line == null) {
                throw invalidFile(path, lineNumber, "File ended before the declared record count.");
            }
            PIR record;
            try {
                record = RecordCodec.decodeRecord(line);
            } catch (ValidationException exception) {
                throw new StorageException(location(path, lineNumber) + exception.getMessage(), exception);
            }
            long id = record.getId();
            if (!seenIds.add(id)) {
                throw invalidFile(path, lineNumber, "Duplicate record ID " + id + ".");
            }
            if (id >= metadata.nextId()) {
                throw invalidFile(path, lineNumber, "Record ID " + id + " must be below nextId.");
            }
            records.add(record);
        }
        long lineNumber = (long) metadata.recordCount() + 3;
        if (readLine(reader, path, lineNumber) != null) {
            throw invalidFile(path, lineNumber, "Content follows the declared records.");
        }
        return records;
    }

    private static String readLine(BufferedReader reader, Path path, long lineNumber)
            throws IOException, StorageException {
        StringBuilder line = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1 && character != '\n') {
            if (character == '\r') {
                if (reader.read() != '\n') {
                    throw invalidFile(path, lineNumber, "Expected LF or CRLF line endings.");
                }
                return line.toString();
            }
            line.append((char) character);
        }
        return character == -1 && line.isEmpty() ? null : line.toString();
    }

    private static StorageException invalidFile(Path path, long lineNumber, String message) {
        return new StorageException(location(path, lineNumber) + message);
    }

    private static String location(Path path, long lineNumber) {
        return "Invalid PIM file '" + path + "' at line " + lineNumber + ": ";
    }

    private record Metadata(long nextId, int recordCount) {
    }
}
