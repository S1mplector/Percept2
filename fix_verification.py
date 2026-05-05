import sys

window_file = "editor/src/main/java/com/jvn/editor/ui/actioneditor/PuppeteerWindow.java"
with open(window_file, "r") as f:
    content = f.read()

# 1. modify showVerificationOverlay signature and body
old_show_verification = """    private void showVerificationOverlay(
        String title,
        String header,
        List<TimelineDiagnostic.Message> findings,
        boolean allowContinue,
        Runnable onContinue
    ) {
        TextArea body = new TextArea(formatVerificationMessages(findings));
        body.setEditable(false);
        body.setWrapText(true);
        body.setFocusTraversable(false);
        body.setPrefRowCount(Math.min(18, Math.max(8, findings == null ? 8 : findings.size() + 2)));
        body.setStyle("-fx-control-inner-background: #121212; -fx-text-fill: #d7d7d7; -fx-font-family: Monospaced;");
        if (allowContinue) {
            overlayDialog.showDialog(
                title,
                header,
                body,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.accent("Continue", () -> {
                    if (onContinue != null) {
                        onContinue.run();
                    }
                    overlayDialog.hideOverlay();
                })
            );
        } else {
            overlayDialog.showDialog(
                title,
                header,
                body,
                ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
            );
        }
    }"""

new_show_verification = """    private void showVerificationOverlay(
        String title,
        String header,
        List<TimelineDiagnostic.Message> findings,
        String continueText,
        Runnable onContinue
    ) {
        TextArea body = new TextArea(formatVerificationMessages(findings));
        body.setEditable(false);
        body.setWrapText(true);
        body.setFocusTraversable(false);
        body.setPrefRowCount(Math.min(18, Math.max(8, findings == null ? 8 : findings.size() + 2)));
        body.setStyle("-fx-control-inner-background: #121212; -fx-text-fill: #d7d7d7; -fx-font-family: Monospaced;");
        if (continueText != null) {
            overlayDialog.showDialog(
                title,
                header,
                body,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.accent(continueText, () -> {
                    if (onContinue != null) {
                        onContinue.run();
                    }
                    overlayDialog.hideOverlay();
                })
            );
        } else {
            overlayDialog.showDialog(
                title,
                header,
                body,
                ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
            );
        }
    }"""

content = content.replace(old_show_verification, new_show_verification)

# 2. modify requestRegisterTimeline usages
old_request_errors = """        if (hasErrors) {
            showVerificationOverlay(
                "Registration Blocked",
                "Puppeteer found runtime registration errors. Fix them before registering this timeline.",
                findings,
                false,
                null
            );
            return;
        }
        if (hasWarnings) {
            showVerificationOverlay(
                "Register Timeline?",
                "Puppeteer found warnings that may affect runtime playback. Continue registering anyway?",
                findings,
                true,
                () -> performRegisterTimeline(name, onSuccess)
            );
            return;
        }"""

new_request_errors = """        if (hasErrors) {
            showVerificationOverlay(
                "Registration Blocked",
                "Puppeteer found runtime registration errors. Fix them before registering this timeline.",
                findings,
                null,
                null
            );
            return;
        }
        if (hasWarnings) {
            showVerificationOverlay(
                "Register Timeline?",
                "Puppeteer found warnings that may affect runtime playback. Continue registering anyway?",
                findings,
                "Continue",
                () -> performRegisterTimeline(name, onSuccess)
            );
            return;
        }"""

content = content.replace(old_request_errors, new_request_errors)

# 3. modify showRuntimeVerificationReport
old_report = """    private void showRuntimeVerificationReport() {
        List<TimelineDiagnostic.Message> findings = PuppeteerVerification.diagnose(
            project,
            knownSceneEntities(),
            projectRoot,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );
        if (findings.isEmpty()) {
            findings = List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.INFO,
                "(timeline)",
                "No runtime registration issues found",
                "This timeline is ready to register with the current project state"
            ));
        }
        showVerificationOverlay(
            "Runtime Verification",
            "Puppeteer checked this timeline against runtime registration rules.",
            findings,
            false,
            null
        );
    }"""

new_report = """    private void showRuntimeVerificationReport() {
        List<TimelineDiagnostic.Message> findings = PuppeteerVerification.diagnose(
            project,
            knownSceneEntities(),
            projectRoot,
            PuppeteerVerification.Mode.REGISTER_RUNTIME
        );
        boolean hasErrors = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.ERROR);
        boolean hasWarnings = findings.stream().anyMatch(message -> message.severity() == TimelineDiagnostic.Severity.WARNING);

        if (findings.isEmpty()) {
            findings = List.of(new TimelineDiagnostic.Message(
                TimelineDiagnostic.Severity.INFO,
                "(timeline)",
                "No runtime registration issues found",
                "This timeline is ready to register with the current project state"
            ));
        }

        if (hasErrors) {
            showVerificationOverlay(
                "Runtime Verification (Errors Found)",
                "Puppeteer checked this timeline and found errors blocking registration.",
                findings,
                null,
                null
            );
        } else if (hasWarnings) {
            showVerificationOverlay(
                "Runtime Verification (Warnings Found)",
                "Puppeteer checked this timeline and found potential issues. Proceed with registration?",
                findings,
                "Register Anyway",
                () -> requestRegisterTimeline() 
            );
        } else {
            showVerificationOverlay(
                "Runtime Verification Passed",
                "Puppeteer checked this timeline. It is ready for runtime registration.",
                findings,
                "Register Now",
                () -> requestRegisterTimeline()
            );
        }
    }"""

content = content.replace(old_report, new_report)

with open(window_file, "w") as f:
    f.write(content)

