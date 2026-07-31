package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import javafx.scene.control.ListView;
import javafx.scene.layout.FlowPane;

class RunConsoleViewMigrationContractTest {

    @Test
    void consoleOutputUsesListViewContract() throws Exception {
        // Verify RunConsoleView class uses ListView for per-line styled output
        var fields = RunConsoleView.class.getDeclaredFields();
        boolean hasListViewField = false;
        for (var field : fields) {
            if (field.getType().equals(ListView.class)) {
                hasListViewField = true;
                break;
            }
        }
        assertTrue(hasListViewField, "RunConsoleView should have a ListView field for output");
    }

    @Test
    void runtimeToolbarAvoidsTheJavaFxToolbarOverflowSkin() throws Exception {
        var method = RunConsoleView.class.getDeclaredMethod("createToolBar");

        assertEquals(FlowPane.class, method.getReturnType());
    }
}
