package com.avioconsulting.mule.opentelemetry.test.util;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class TestLoggerHandler extends Handler {

  protected final java.util.Queue<String> CAPTURED = new ConcurrentLinkedDeque<>();

  public Collection<String> getCapturedLogs() {
    return Collections.unmodifiableCollection(CAPTURED);
  }

  @Override
  public void publish(LogRecord record) {
    CAPTURED.add(record.getMessage());
  }

  @Override
  public void flush() {

  }

  @Override
  public void close() throws SecurityException {

  }
}
