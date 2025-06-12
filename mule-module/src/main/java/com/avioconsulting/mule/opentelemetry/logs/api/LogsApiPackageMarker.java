package com.avioconsulting.mule.opentelemetry.logs.api;

/**
 * This is a marker class to allow exporting the Logs API Package for Log
 * implementations. This will result in exporting this whole package and
 * thus avoids a compile-time dependency on the actual Log support APIs.
 *
 * Log support libraries should use the same package for auto-exporting any
 * classes.
 */
public final class LogsApiPackageMarker {
}
