# COMP3211 — Personal Information Manager System 

A local command-line PIM following the Group 44 design. The application uses
only the Java standard library. The build targets Java 17.

## Build and run

From this directory, with a JDK (17 or newer) on your PATH:

    ./scripts/build.sh
    java -jar build/pim.jar

At the prompt, enter:

    create note text="Review the project requirements"
    search type = note && text contains "project"
    save "notes.pim"
    exit

Run all JUnit 4.13.1 tests with `./scripts/test.sh`. The test dependencies
are bundled in `lib/test/` for offline use. The script runs the model tests
and CLI tests separately.

In IntelliJ IDEA, open the PIM directory and run `app.Main` to start the
application. Use the gutter Run button beside a test class or `@Test` method
to run it with JUnit.

## Source layout

- src/main/java/app wires the application.
- src/main/java/model owns records, validation, search, and persistence.
- src/main/java/controller parses commands and manages the session.
- src/main/java/view reads the console and formats output.
- src/test/java/tests contains model unit tests and console scenarios.
- lib/test contains JUnit and Hamcrest for the test classpath.
- scripts contains the build and test entry points.
- docs contains the design and both manuals. The manual and report sources are in docs/source.
- Requirements-Coverage.pdf and Model-Line-Coverage.pdf are at the source root,
  as required by the project brief.

The model uses immutable records and replaces a record only after complete
validation. Lookup and mutation use an ID map. Search parses one criterion,
scans the collection, and sorts only the matching records. Loading validates a
candidate snapshot before installing it. Save writes a sibling temporary file
before replacing the destination.

## Course submission assembly

The project brief requires one ZIP for the group. Suggested layout:

    Group44.zip
    ├── Honour-Declaration.pdf
    ├── SRS.pdf
    ├── PIM/
    │   ├── src/
    │   ├── scripts/
    │   ├── lib/test/
    │   ├── docs/
    │   │   ├── Design-Document.pdf
    │   │   ├── Developer-Manual.pdf
    │   │   └── User-Manual.pdf
    │   ├── Requirements-Coverage.pdf
    │   ├── Model-Line-Coverage.pdf
    │   └── README.md
    ├── Demo.mp4
    └── Presentation/
        ├── Slides.pdf
        └── Recording.mp4

Exclude generated build/ and out/ directories. The team supplies the final
SRS, demonstration, presentation, and completed declaration. Update the
requirements coverage report with the final SRS identifiers before submission.
Record the GenAI assistance used for code, tests, and documents in the group's
declaration.
