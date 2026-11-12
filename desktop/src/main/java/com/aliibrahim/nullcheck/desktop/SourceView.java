package com.aliibrahim.nullcheck.desktop;

import com.aliibrahim.nullcheck.core.AnalysisResult;
import com.aliibrahim.nullcheck.core.IfStatement;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Read-only source listing with line numbers. Lines with an {@code if} statement that checks for {@code null}
 * are highlighted, and other {@code if} lines are marked more lightly.
 */
final class SourceView extends ListView<String> {

    private final Set<Integer> nullCheckLines = new HashSet<>();
    private final Set<Integer> ifLines = new HashSet<>();
    private int digits = 1;

    SourceView() {
        getStyleClass().add("source-view");
        setFixedCellSize(22);
        setCellFactory(list -> new LineCell());
        setPlaceholder(new Label("Open a source file (Ctrl+O) or a folder (Ctrl+Shift+O), or drop one here."));
    }

    /** Shows the source of a result, or clears the view when {@code result} is {@code null}. */
    void show(AnalysisResult result) {
        nullCheckLines.clear();
        ifLines.clear();
        if (result == null) {
            getItems().clear();
            return;
        }
        for (IfStatement s : result.ifStatements()) {
            (s.checksNull() ? nullCheckLines : ifLines).add(s.line());
        }
        List<String> lines = result.source().lines().map(l -> l.replace("\t", "    ")).toList();
        digits = String.valueOf(Math.max(lines.size(), 1)).length();
        getItems().setAll(lines);
        scrollTo(0);
    }

    /** Selects a 1-based line and scrolls so that it is visible with some context above it. */
    void reveal(int line) {
        if (line < 1 || line > getItems().size()) {
            return;
        }
        getSelectionModel().clearAndSelect(line - 1);
        scrollTo(Math.max(0, line - 6));
    }

    private final class LineCell extends ListCell<String> {
        private final Label number = new Label();
        private final Label code = new Label();
        private final HBox box = new HBox(number, code);

        LineCell() {
            number.getStyleClass().add("line-number");
            number.setAlignment(Pos.CENTER_RIGHT);
            code.getStyleClass().add("code");
            code.setMinWidth(Region.USE_PREF_SIZE);
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(String text, boolean empty) {
            super.updateItem(text, empty);
            getStyleClass().removeAll("null-check-line", "if-line");
            setTooltip(null);
            if (empty || text == null) {
                setGraphic(null);
                return;
            }
            int line = getIndex() + 1;
            number.setText(String.format(Locale.ROOT, "%" + digits + "d", line));
            number.setMinWidth(digits * 8 + 16);
            code.setText(text.isEmpty() ? " " : text);
            if (nullCheckLines.contains(line)) {
                getStyleClass().add("null-check-line");
                setTooltip(new Tooltip("Null check"));
            } else if (ifLines.contains(line)) {
                getStyleClass().add("if-line");
            }
            setGraphic(box);
        }
    }
}
