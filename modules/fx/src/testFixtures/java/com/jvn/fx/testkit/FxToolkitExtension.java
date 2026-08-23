package com.jvn.fx.testkit;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for tests that need the JavaFX toolkit. Starts it once per JVM via
 * {@link FxToolkit#ensureStarted()} and skips the test class when it is unavailable (e.g.
 * a headless Linux CI agent), instead of every {@code @Test} method repeating
 * {@code Assumptions.assumeTrue(toolkitAvailable, ...)}.
 *
 * <p>Usage: {@code @ExtendWith(FxToolkitExtension.class)} on the test class, then call
 * {@link FxToolkit#runFx} from test bodies to run work on the FX application thread.
 */
public final class FxToolkitExtension implements BeforeAllCallback, ExecutionCondition {
  @Override
  public void beforeAll(ExtensionContext context) {
    FxToolkit.ensureStarted();
  }

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (FxToolkit.ensureStarted()) {
      return ConditionEvaluationResult.enabled("JavaFX toolkit is available");
    }
    return ConditionEvaluationResult.disabled("JavaFX toolkit is unavailable in this environment");
  }
}
