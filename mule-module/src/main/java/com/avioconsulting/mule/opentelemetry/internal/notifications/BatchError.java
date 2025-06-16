package com.avioconsulting.mule.opentelemetry.internal.notifications;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.api.message.Message;

import java.util.Collections;
import java.util.List;

/**
 * An adapter to wrap Exceptions raised by Batch processing and provide an
 * instance of {@link Error}.
 * OTEL Span processing requires Error instance when processing operations. See
 * {@link com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection#endProcessorSpan(TraceComponent, Error)}
 */
public class BatchError implements Error {

  private final Exception exception;
  private final MuleException muleException;

  private BatchError(Exception exception) {
    this.exception = exception;
    if (exception instanceof MuleException) {
      this.muleException = (MuleException) exception;
    } else {
      muleException = null;
    }
  }

  public static Error of(Exception exception) {
    return exception == null ? null : new BatchError(exception);
  }

  @Override
  public String getDescription() {
    return exception.getMessage();
  }

  @Override
  public String getDetailedDescription() {
    return muleException != null ? muleException.getDetailedMessage() : exception.getMessage();
  }

  @Override
  public ErrorType getErrorType() {
    return (muleException != null && muleException.getExceptionInfo() != null)
        ? muleException.getExceptionInfo().getErrorType()
        : null;
  }

  @Override
  public Throwable getCause() {
    return exception.getCause();
  }

  @Override
  public Message getErrorMessage() {
    return null;
  }

  @Override
  public List<Error> getChildErrors() {
    return Collections.emptyList();
  }
}
