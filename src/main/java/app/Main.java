package app;

import controller.CommandParser;
import controller.PimController;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import model.RecordBook;
import model.criteria.CriterionParser;
import model.persistence.PimFileStorage;
import view.ConsoleView;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        ConsoleView view = new ConsoleView(input, output);
        PimController controller = new PimController(
                new RecordBook(), new CriterionParser(), new PimFileStorage(), new CommandParser(), view);
        controller.run();
    }
}
