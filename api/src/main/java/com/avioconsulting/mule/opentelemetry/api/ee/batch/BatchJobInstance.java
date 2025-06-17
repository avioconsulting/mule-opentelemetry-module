package com.avioconsulting.mule.opentelemetry.api.ee.batch;

import java.util.Date;

public interface BatchJobInstance {
  String getId();

  BatchJobResult getResult();

  BatchJobInstanceStatus getStatus();

  long getRecordCount();

  String getOwnerJobName();

  Date getCreationTime();

}
