package view;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import model.records.FieldName;
import model.records.PIR;
import model.records.RecordTime;

/** Renders records and reads terminal input without owning application state. */
public final class ConsoleView {
    private final BufferedReader input;
    private final PrintStream output;
    private boolean inputClosed;

    public ConsoleView(BufferedReader input, PrintStream output) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
    }

    public Optional<String> readCommand() throws IOException {
        return readLine("pim> ");
    }

    public boolean confirm(String prompt) throws IOException {
        Optional<String> line = readLine(prompt + " [y/yes] ");
        if (line.isEmpty()) {
            return false;
        }
        String answer = line.get().strip();
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    public void showRecord(PIR record) {
        output.println("  id: " + record.getId());
        output.println("  type: " + record.getType().commandName());
        for (FieldName field : record.getType().fields()) {
            if (field.isText()) {
                showField(field, record.getTextField(field).orElseThrow());
            } else {
                showField(field, RecordTime.format(record.getTimeField(field).orElseThrow()));
            }
        }
    }

    public void showRecords(List<PIR> records) {
        int size = records.size();
        if (size == 0) {
            output.println("No matching records.");
            return;
        }
        output.println(size == 1 ? "1 matching record:" : size + " matching records:");
        boolean first = true;
        for (PIR record : records) {
            if (!first) {
                output.println();
            }
            showRecord(record);
            first = false;
        }
    }

    public void showMessage(String message) {
        output.println(message);
    }

    public void showError(String message) {
        output.println("Error: " + message);
    }

    public void showWelcome() {
        output.print("""
                =====================================
                    Personal Information Manager
                =====================================
                Type help for available commands.
                Try: create note text="Buy groceries"
                """);
    }

    public void showHelp() {
        output.print("""
                Commands
                  create note text="value"
                  create task description="value" deadline="2026-11-18 16:00"
                  create event description="value" start="2026-11-18 16:00" alarm="2026-11-18 15:30"
                  create contact name="value" address="value" mobile="value"
                  update <id> <field="value"> ...
                  search <expression>
                  print <id|all>
                  delete <id>
                  save "<path.pim>"
                  load "<path.pim>"
                  help
                  help search
                  exit

                Search by type, text contains, or time <, >, and =.
                Use parentheses, !, &&, and || in descending precedence.
                Example: search type = event && description contains "consultation"
                Text matching is case-sensitive. Times use uuuu-MM-dd HH:mm.
                """);
        output.println("Use \\\" for a quote and \\\\ for a backslash inside quoted values.");
    }

    public void showSearchHelp() {
        output.print("""
                Find records with search <expression>

                  search type = note && text contains "project"
                  search type = task && deadline < "2026-11-20 20:00"
                  search type = event && !(description contains "cancelled")

                Time comparisons use <, > or =. Enter times as "uuuu-MM-dd HH:mm".
                Text matching is case-sensitive.
                Use parentheses to group conditions. Precedence is !, then &&, then ||.
                A comparison is false when a record has no such field.
                Use a type check to limit a negated comparison to one record type.
                """);
    }

    private void showField(FieldName field, String value) {
        output.println("  " + field.commandName() + ": " + value);
    }

    private Optional<String> readLine(String prompt) throws IOException {
        if (inputClosed) {
            return Optional.empty();
        }
        output.print(prompt);
        // The prompt has no newline. Show it before waiting for input.
        output.flush();
        try {
            String line = input.readLine();
            inputClosed = line == null;
            return Optional.ofNullable(line);
        } catch (IOException failure) {
            // A failed confirmation read must not lead to another command prompt.
            inputClosed = true;
            throw failure;
        }
    }
}
