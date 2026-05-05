package com.jvn.core.vn.script;

import java.io.IOException;
import java.util.List;

public class MultipleParseErrorsException extends IOException {
  private final List<VnParseException> errors;

  public MultipleParseErrorsException(List<VnParseException> errors) {
    super(errors.size() + " parse errors occurred");
    this.errors = errors;
  }

  public List<VnParseException> getErrors() {
    return errors;
  }
}
