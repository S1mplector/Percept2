package com.jvn.core.vn.script;

import java.io.IOException;
import java.util.List;

public class MultipleParseErrorsException extends IOException {
  private final List<VnParseException> errors;

  public MultipleParseErrorsException(List<VnParseException> errors) {
    super(buildMessage(errors));
    this.errors = errors;
  }

  public List<VnParseException> getErrors() {
    return errors;
  }

  private static String buildMessage(List<VnParseException> errors) {
    if (errors == null || errors.isEmpty()) {
      return "0 parse errors occurred";
    }
    StringBuilder message = new StringBuilder(errors.size())
        .append(" parse errors occurred");
    for (VnParseException error : errors) {
      if (error == null) continue;
      String detail = error.getDetailMessage();
      if (detail == null || detail.isBlank()) {
        detail = error.getMessage();
      }
      if (detail == null || detail.isBlank()) continue;
      message.append(System.lineSeparator()).append("- ").append(detail);
    }
    return message.toString();
  }
}
