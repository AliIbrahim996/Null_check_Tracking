package com.aliibrahim.nullcheck.desktop;

import com.aliibrahim.nullcheck.core.AnalysisResult;
import com.aliibrahim.nullcheck.core.ComparandKind;
import com.aliibrahim.nullcheck.core.Definition;
import com.aliibrahim.nullcheck.core.FileAnalysis;
import com.aliibrahim.nullcheck.core.IfStatement;
import com.aliibrahim.nullcheck.core.Language;
import com.aliibrahim.nullcheck.core.NameScope;
import com.aliibrahim.nullcheck.core.NullCheckAnalyzer;
import com.aliibrahim.nullcheck.core.NullComparand;
import com.aliibrahim.nullcheck.core.ProjectAnalysis;
import com.aliibrahim.nullcheck.core.ProjectAnalyzer;
import com.aliibrahim.nullcheck.core.Reports;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * The main window content: a toolbar, the source listing on the left, the analysis tabs on the right and a
 * status bar. Analysis and PDF export run on background threads.
 */
final class MainView extends BorderPane {

    static final String APP_NAME = "Null Check Tracking";

    private final Stage stage;
    private final NullCheckAnalyzer analyzer = new NullCheckAnalyzer();

    private final ProjectAnalyzer projectAnalyzer = new ProjectAnalyzer();

    private final SourceView sourceView = new SourceView();
    private final TableView<FileAnalysis> fileTable = new TableView<>();
    private final SplitPane content = new SplitPane();
    private final TabPane tabs = new TabPane();
    private final TableView<NullComparand> comparandTable = new TableView<>();
    private final TableView<IfStatement> ifTable = new TableView<>();
    private final TableView<Definition> adviceTable = new TableView<>();
    private final Tab comparandTab = new Tab();
    private final Tab ifTab = new Tab();
    private final Tab adviceTab = new Tab();
    private final VBox statistics = new VBox(16);

    private final Button exportButton = new Button("Export PDF…");
    private final Button copyButton = new Button("Copy report");
    private final Label fileLabel = new Label("No file open");
    private final Label status = new Label("Ready");

    private AnalysisResult result;
    private Path file;
    /** The open folder, or {@code null} when a single file is open. */
    private ProjectAnalysis project;
    private File lastDirectory;
    /** Counts calls of {@link #open(Path)} and {@link #openFolder(Path)}; only touched on the JavaFX thread. */
    private long openRequests;

    MainView(Stage stage) {
        this.stage = stage;
        getStyleClass().add("main-view");
        setTop(buildToolBar());
        setCenter(buildContent());
        setBottom(buildStatusBar());
        setUpDragAndDrop();
        showProject(null);
        show(null, null);

        // Start dialogs in the working directory (the samples folder when started with ./gradlew run).
        Path workingDirectory = Path.of("").toAbsolutePath();
        lastDirectory = Files.isDirectory(workingDirectory) ? workingDirectory.toFile() : null;
    }

    // ---------------------------------------------------------------- layout

    private ToolBar buildToolBar() {
        Button openButton = new Button("Open…");
        openButton.setOnAction(e -> chooseAndOpen());
        openButton.setTooltip(new Tooltip("Open a Java, PHP or C++ source file (Ctrl+O)"));
        openButton.getStyleClass().add("accent");
        Button openFolderButton = new Button("Open folder…");
        openFolderButton.setOnAction(e -> chooseAndOpenFolder());
        openFolderButton.setTooltip(new Tooltip("Analyse every source file in a folder (Ctrl+Shift+O)"));

        exportButton.setOnAction(e -> exportPdf());
        exportButton.setTooltip(new Tooltip("Save the report as a PDF file (Ctrl+E)"));
        copyButton.setOnAction(e -> copyReport());
        copyButton.setTooltip(new Tooltip("Copy the report as plain text"));

        fileLabel.getStyleClass().add("file-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        stage.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
                        this::chooseAndOpen);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN,
                        KeyCombination.SHIFT_DOWN), this::chooseAndOpenFolder);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN),
                        () -> {
                            if (!exportButton.isDisabled()) {
                                exportPdf();
                            }
                        });
            }
        });
        return new ToolBar(openButton, openFolderButton, exportButton, copyButton,
                new Separator(Orientation.VERTICAL),
                fileLabel, spacer);
    }

    private SplitPane buildContent() {
        fileTable.getColumns().setAll(List.of(
                column("File", FileAnalysis::displayPath, 150, "code"),
                column("Language", f -> f.language().label(), 80, null),
                column("Checks", f -> f.result().comparands().size(), 60, "number")));
        fileTable.setPlaceholder(new Label("No Java, PHP or C++ files in this folder."));
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, old, f) -> {
            if (f != null && project != null) {
                show(project.root().resolve(f.file()), f.result());
            }
        });

        comparandTable.getColumns().setAll(List.of(
                column("Line", NullComparand::line, 60, "number"),
                column("Expression", NullComparand::expression, 260, "code"),
                column("Kind", c -> c.kind().label(), 110, null),
                column("Scope", c -> c.scope() == null ? "–" : c.scope().label(), 100, null)));
        comparandTable.setPlaceholder(new Label("No null checks found."));
        comparandTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, c) -> revealIfSelected(c == null ? 0 : c.line()));

        ifTable.getColumns().setAll(List.of(
                column("Line", IfStatement::line, 60, "number"),
                column("Condition", IfStatement::condition, 380, "code"),
                column("Null check", s -> s.checksNull() ? "yes" : "", 90, null)));
        ifTable.setPlaceholder(new Label("No if statements found."));
        ifTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, s) -> revealIfSelected(s == null ? 0 : s.line()));

        adviceTable.getColumns().setAll(List.of(
                column("Line", Definition::line, 60, "number"),
                column("Comparand", Definition::comparand, 130, "code"),
                column("Code", Definition::code, 240, "code"),
                column("Advice", Definition::advice, 460, null)));
        adviceTable.setPlaceholder(new Label("No definitions found before the null checks."));
        adviceTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, d) -> revealIfSelected(d == null ? 0 : d.line()));

        for (TableView<?> table : List.of(comparandTable, ifTable, adviceTable)) {
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        }

        comparandTab.setContent(comparandTable);
        ifTab.setContent(ifTable);
        adviceTab.setContent(adviceTable);
        statistics.setPadding(new Insets(16));
        ScrollPane statisticsScroll = new ScrollPane(statistics);
        statisticsScroll.setFitToWidth(true);
        Tab statisticsTab = new Tab("Statistics", statisticsScroll);

        tabs.getTabs().setAll(comparandTab, ifTab, adviceTab, statisticsTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return content;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(status);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private static <S, T> TableColumn<S, T> column(String title, Function<S, T> value, double width,
                                                  String styleClass) {
        TableColumn<S, T> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        // Show the full text in a tooltip, since long code and advice get cut off.
        column.setCellFactory(c -> new TableCell<>() {
            private final Tooltip tooltip = new Tooltip();

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                String text = empty || item == null ? null : item.toString();
                setText(text);
                if (text == null || text.length() < 20) {
                    setTooltip(null);
                } else {
                    tooltip.setText(text);
                    setTooltip(tooltip);
                }
            }
        });
        if (styleClass != null) {
            column.getStyleClass().add(styleClass);
        }
        return column;
    }

    private void setUpDragAndDrop() {
        setOnDragOver(e -> {
            if (e.getDragboard().hasFiles() && droppable(e.getDragboard().getFiles()).isPresent()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        setOnDragDropped(e -> {
            Optional<File> dropped = e.getDragboard().hasFiles()
                    ? droppable(e.getDragboard().getFiles()) : Optional.empty();
            dropped.ifPresent(f -> {
                if (f.isDirectory()) {
                    openFolder(f.toPath());
                } else {
                    open(f.toPath());
                }
            });
            e.setDropCompleted(dropped.isPresent());
            e.consume();
        });
    }

    /** Returns the first dropped folder or supported source file. */
    private static Optional<File> droppable(List<File> files) {
        return files.stream()
                .filter(f -> f.isDirectory() || (f.isFile() && Language.forFileName(f.getName()).isPresent()))
                .findFirst();
    }

    // ---------------------------------------------------------------- actions

    private void chooseAndOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open source file");
        List<String> all = new ArrayList<>();
        List<FileChooser.ExtensionFilter> perLanguage = new ArrayList<>();
        for (Language language : Language.values()) {
            List<String> patterns = language.extensions().stream().map(e -> "*." + e).toList();
            all.addAll(patterns);
            perLanguage.add(new FileChooser.ExtensionFilter(language.label() + " files", patterns));
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Supported source files", all));
        chooser.getExtensionFilters().addAll(perLanguage);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File chosen = chooser.showOpenDialog(stage);
        if (chosen != null) {
            open(chosen.toPath());
        }
    }

    /** Analyses a file in the background and shows the result. */
    void open(Path path) {
        // Only the most recent open may show its result; an older, slower analysis is ignored when it finishes.
        long request = ++openRequests;
        status.setText("Analysing " + path.getFileName() + "…");
        runInBackground(() -> analyzer.analyze(path), analysed -> {
            if (request != openRequests) {
                return;
            }
            Path parent = path.toAbsolutePath().getParent();
            lastDirectory = parent == null ? lastDirectory : parent.toFile();
            showProject(null);
            show(path, analysed);
            status.setText("Found " + analysed.comparands().size() + " null checks in "
                    + analysed.ifStatements().size() + " if statements.");
        }, () -> request == openRequests, "Could not analyse " + path.getFileName());
    }

    private void chooseAndOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open folder");
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            openFolder(chosen.toPath());
        }
    }

    /** Analyses every supported file in a folder in the background and shows the folder. */
    void openFolder(Path folder) {
        long request = ++openRequests;
        status.setText("Analysing the files in " + folder.toAbsolutePath() + "…");
        runInBackground(() -> projectAnalyzer.analyze(folder), analysed -> {
            if (request != openRequests) {
                return;
            }
            lastDirectory = folder.toAbsolutePath().toFile();
            showProject(analysed);
            AnalysisResult total = analysed.total();
            status.setText("Found " + total.comparands().size() + " null checks in " + total.ifStatements().size()
                    + " if statements in " + analysed.files().size() + " files"
                    + (analysed.errors().isEmpty() ? "." : "; " + analysed.errors().size() + " could not be read."));
        }, () -> request == openRequests, "Could not analyse " + folder.getFileName());
    }

    private void exportPdf() {
        if (result == null && project == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName((project != null ? project.name() : baseName()) + "-null-checks.pdf");
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }
        AnalysisResult current = result;
        ProjectAnalysis currentProject = project;
        String name = file == null ? "" : file.getFileName().toString();
        status.setText("Writing " + target.getName() + "…");
        runInBackground(() -> {
            if (currentProject != null) {
                Reports.writePdf(currentProject, target.toPath());
            } else {
                Reports.writePdf(current, name, target.toPath());
            }
            return target;
        }, written -> status.setText("PDF report saved to " + written), () -> true, "Could not save the PDF report");
    }

    private void copyReport() {
        if (result == null && project == null) {
            return;
        }
        ClipboardContent clip = new ClipboardContent();
        clip.putString(project != null ? Reports.toText(project)
                : Reports.toText(result, file.getFileName().toString()));
        Clipboard.getSystemClipboard().setContent(clip);
        status.setText("Report copied to the clipboard.");
    }

    /**
     * Runs {@code work} on a background thread, then {@code onSuccess} on the JavaFX thread. A failure is reported
     * only while {@code stillWanted} is true, so that a superseded task does not show an error.
     */
    private <T> void runInBackground(Callable<T> work, Consumer<T> onSuccess, BooleanSupplier stillWanted,
                                     String errorTitle) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            if (!stillWanted.getAsBoolean()) {
                return;
            }
            Throwable error = task.getException();
            status.setText(errorTitle + ".");
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    error == null || error.getMessage() == null ? String.valueOf(error) : error.getMessage());
            alert.initOwner(stage);
            alert.setHeaderText(errorTitle);
            alert.showAndWait();
        });
        Thread thread = new Thread(task, "null-check-worker");
        thread.setDaemon(true);
        thread.start();
    }

    // ---------------------------------------------------------------- showing results

    /** Shows a folder with its file list, or switches to the single-file layout when {@code analysed} is null. */
    private void showProject(ProjectAnalysis analysed) {
        this.project = analysed;
        if (analysed == null) {
            fileTable.getItems().clear();
            content.getItems().setAll(sourceView, tabs);
            content.setDividerPositions(0.5);
            return;
        }
        fileTable.getItems().setAll(analysed.files());
        content.getItems().setAll(fileTable, sourceView, tabs);
        content.setDividerPositions(0.24, 0.6);
        if (analysed.files().isEmpty()) {
            show(null, null);
            fileLabel.setText(analysed.root().toAbsolutePath() + " (no supported files)");
        } else {
            FileAnalysis first = analysed.files().stream()
                    .filter(f -> !f.result().comparands().isEmpty()).findFirst().orElse(analysed.files().get(0));
            fileTable.getSelectionModel().select(first);
            fileTable.scrollTo(first);
        }
    }

    private void show(Path path, AnalysisResult analysed) {
        this.file = path;
        this.result = analysed;
        boolean empty = analysed == null;
        exportButton.setDisable(empty && project == null);
        copyButton.setDisable(empty && project == null);
        fileLabel.setText(empty ? "No file open" : path.toAbsolutePath().toString());
        String title = project != null ? project.name() + (empty ? "" : " › " + path.getFileName())
                : empty ? null : path.getFileName().toString();
        stage.setTitle(title == null ? APP_NAME : title + " – " + APP_NAME);

        sourceView.show(analysed);
        comparandTable.getItems().setAll(empty ? List.of() : analysed.comparands());
        ifTable.getItems().setAll(empty ? List.of() : analysed.ifStatements());
        adviceTable.getItems().setAll(empty ? List.of() : analysed.definitions());
        comparandTab.setText("Null checks (" + comparandTable.getItems().size() + ")");
        ifTab.setText("If statements (" + ifTable.getItems().size() + ")");
        adviceTab.setText("Advice (" + adviceTable.getItems().size() + ")");
        showStatistics(analysed);
    }

    private void showStatistics(AnalysisResult r) {
        statistics.getChildren().clear();
        if (r == null && project == null) {
            statistics.getChildren().add(new Label("Open a Java, PHP or C++ file or a folder to see statistics."));
            return;
        }
        if (r != null) {
            if (project != null) {
                statistics.getChildren().add(heading("This file"));
            }
            addStatistics(r);
        }
        if (project != null) {
            statistics.getChildren().add(heading("Whole folder (" + project.files().size() + " files)"));
            addStatistics(project.total());
        }
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("statistics-heading");
        return label;
    }

    private void addStatistics(AnalysisResult r) {
        FlowPane cards = new FlowPane(12, 12,
                card(String.valueOf(r.ifStatements().size()), "if statements"),
                card(r.percentOfIfsCheckingNull() + "%", "of them check for null"),
                card(String.valueOf(r.comparands().size()), "null comparands"),
                card(String.valueOf(r.definitions().size()), "definitions before a check"));

        ObservableList<PieChart.Data> kinds = FXCollections.observableArrayList();
        for (ComparandKind kind : ComparandKind.values()) {
            long count = r.comparands().stream().filter(c -> c.kind() == kind).count();
            if (count > 0) {
                kinds.add(new PieChart.Data(kind.label() + " (" + count + ")", count));
            }
        }
        ObservableList<PieChart.Data> scopes = FXCollections.observableArrayList();
        for (NameScope scope : NameScope.values()) {
            long count = r.comparands().stream().filter(c -> c.scope() == scope).count();
            if (count > 0) {
                scopes.add(new PieChart.Data(scope.label() + " (" + count + ")", count));
            }
        }
        statistics.getChildren().add(cards);
        if (kinds.isEmpty()) {
            statistics.getChildren().add(new Label("No null checks to chart."));
            return;
        }
        FlowPane charts = new FlowPane(12, 12, chart("Comparand kinds", kinds), chart("Name scopes", scopes));
        statistics.getChildren().add(charts);
    }

    private static VBox card(String value, String caption) {
        Label big = new Label(value);
        big.getStyleClass().add("card-value");
        Label small = new Label(caption);
        small.getStyleClass().add("card-caption");
        VBox card = new VBox(2, big, small);
        card.getStyleClass().add("card");
        return card;
    }

    private static PieChart chart(String title, ObservableList<PieChart.Data> data) {
        PieChart chart = new PieChart(data);
        chart.setTitle(title);
        chart.setLabelsVisible(false);
        chart.setPrefSize(320, 260);
        chart.setAnimated(false);
        return chart;
    }

    private void revealIfSelected(int line) {
        if (line > 0) {
            sourceView.reveal(line);
        }
    }

    private String baseName() {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
