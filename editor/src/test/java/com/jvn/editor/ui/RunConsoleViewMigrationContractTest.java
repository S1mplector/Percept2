package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;

class RunConsoleViewMigrationContractTest {

    @Test
    void consoleOutputUsesTextAreaContract() throws Exception {
        // Verify RunConsoleView class uses TextArea for output
        var fields = RunConsoleView.class.getDeclaredFields();
        boolean hasTextAreaField = false;
        for (var field : fields) {
            if (field.getType().equals(TextArea.class)) {
                hasTextAreaField = true;
                break;
            }
        }
        assertTrue(hasTextAreaField, "RunConsoleView should have a TextArea field for output");
    }
}
