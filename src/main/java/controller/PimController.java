package controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import model.RecordBook;
import model.criteria.Criterion;
import model.criteria.CriterionParser;
import model.exceptions.PimException;
import model.exceptions.StorageException;
import model.persistence.PimFileStorage;
import model.records.FieldName;
import model.records.PIR;
import model.records.RecordType;
import view.ConsoleView;

/** Coordinates commands and owns the unsaved state of the current session. */
public final class PimController {
    private final RecordBook book;
    private final CriterionParser criterionParser;
    private final PimFileStorage storage;
    private final CommandParser commandParser;
    private final ConsoleView view;
    private boolean running;
    private boolean dirty;

    public PimController(RecordBook book, CriterionParser criterionParser,
                         PimFileStorage storage, CommandParser commandParser, ConsoleView view) {
        this.book = Objects.requireNonNull(book, "book");
        this.criterionParser = Objects.requireNonNull(criterionParser, "criterionParser");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.commandParser = Objects.requireNonNull(commandParser, "commandParser");
        this.view = Objects.requireNonNull(view, "view");
    }

    /** Read commands until the user exits or the input stream closes. */
    public void run() {
        running = true;
        view.showWelcome();
        while (running) {
            try {
                Optional<String> line = view.readCommand();
                if (line.isEmpty()) {
                    endInput();
                    return;
                }
                String text = line.get();
                if (text.isBlank()) {
                    continue;
                }
                Command command = commandParser.parse(text);
                execute(command);
            } catch (IOException failure) {
                view.showError("Could not read input: " + failure.getMessage());
                endInput();
            } catch (PimException failure) {
                view.showError(failure.getMessage());
            }
        }
    }

    /** Execute a parsed command. The session loop reports any command errors. */
    public void execute(Command command) throws PimException {
        String body = command.getBody().strip();
        switch (command.getType()) {
            case CREATE -> create(body);
            case UPDATE -> update(body);
            case SEARCH -> {
                // Keep trailing spaces so error positions refer to the full expression.
                Criterion criterion = criterionParser.parse(command.getBody());
                view.showRecords(book.search(criterion));
            }
            case PRINT -> print(body);
            case DELETE -> delete(body);
            case SAVE -> save(body);
            case LOAD -> load(body);
            case HELP -> help(body);
            case EXIT -> {
                requireNoArguments(body, "exit");
                exit();
            }
        }
    }

    private void create(String body) throws PimException {
        int end = CommandParser.wordEnd(body);
        String name = body.substring(0, end);
        RecordType type = RecordType.fromCommandName(name)
                .orElseThrow(() -> new CommandParseException("Expected a record type: note, task, event, or contact."));
        Map<FieldName, String> fields = commandParser.parseFields(body.substring(end));
        PIR created = book.create(type, fields);
        dirty = true;
        view.showMessage("Created record " + created.getId() + ". Unsaved changes.");
    }

    private void update(String body) throws PimException {
        int end = CommandParser.wordEnd(body);
        long id = parseId(body.substring(0, end));
        Map<FieldName, String> changes = commandParser.parseFields(body.substring(end));
        book.update(id, changes);
        // A successful update counts as an edit even when the values are the same.
        dirty = true;
        view.showMessage("Updated record " + id + ". Unsaved changes.");
    }

    private void print(String body) throws PimException {
        if ("all".equals(body)) {
            view.showRecords(book.getAll());
            return;
        }
        try {
            view.showRecord(book.get(parseId(body)));
        } catch (CommandParseException failure) {
            throw new CommandParseException(
                    failure.getMessage() + " Use print <id> or print all. Example: print 1.", failure);
        }
    }

    private void delete(String body) throws PimException {
        long id = parseId(body);
        book.delete(id);
        dirty = true;
        view.showMessage("Deleted record " + id + ". Unsaved changes.");
    }

    private void save(String body) throws PimException {
        Path path = parsePimPath(body);
        if (targetExists(path) && confirmationDeclined("Overwrite the existing file?")) {
            view.showMessage("Save cancelled.");
            return;
        }
        storage.save(path, book.snapshot());
        dirty = false;
        view.showMessage("Saved to " + path + ".");
    }

    private void load(String body) throws PimException {
        Path path = parsePimPath(body);
        if (dirty && confirmationDeclined("Discard unsaved changes and load?")) {
            view.showMessage("Load cancelled.");
            return;
        }
        // Keep the current book until the whole file has been validated.
        book.replaceState(storage.load(path));
        dirty = false;
        view.showMessage("Loaded from " + path + ".");
    }

    private void help(String topic) throws CommandParseException {
        switch (topic) {
            case "" -> view.showHelp();
            case "search" -> view.showSearchHelp();
            default -> throw new CommandParseException("Use help or help search.");
        }
    }

    private void exit() throws CommandParseException {
        if (dirty && confirmationDeclined("Exit and discard unsaved changes?")) {
            view.showMessage("Exit cancelled.");
            return;
        }
        running = false;
    }

    private boolean confirmationDeclined(String prompt) throws CommandParseException {
        try {
            return !view.confirm(prompt);
        } catch (IOException failure) {
            throw new CommandParseException("Could not read confirmation: " + failure.getMessage(), failure);
        }
    }

    private void endInput() {
        running = false;
        if (dirty) {
            view.showMessage("Input ended with unsaved changes. Nothing was saved.");
        }
    }

    private Path parsePimPath(String body) throws CommandParseException {
        String argument = commandParser.parseFileArgument(body);
        Path path;
        try {
            path = Path.of(argument);
        } catch (InvalidPathException failure) {
            throw new CommandParseException("Invalid file path: " + failure.getReason(), failure);
        }
        if (path.getFileName() == null || !path.getFileName().toString().endsWith(".pim")) {
            throw new CommandParseException("The file name must end in .pim.");
        }
        return path;
    }

    private static boolean targetExists(Path path) throws StorageException {
        try {
            // An unreadable path must not be treated as a new file.
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException failure) {
            return false;
        } catch (IOException | SecurityException failure) {
            throw new StorageException("Cannot inspect '" + path + "': " + failure.getMessage(), failure);
        }
    }

    private static long parseId(String text) throws CommandParseException {
        if (text.isEmpty()) {
            throw new CommandParseException("Expected one positive decimal record ID.");
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                throw new CommandParseException("Expected one positive decimal record ID.");
            }
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException failure) {
            throw new CommandParseException("The record ID is too large.", failure);
        }
    }

    private static void requireNoArguments(String body, String command) throws CommandParseException {
        if (!body.isEmpty()) {
            throw new CommandParseException(command + " takes no arguments.");
        }
    }
}
