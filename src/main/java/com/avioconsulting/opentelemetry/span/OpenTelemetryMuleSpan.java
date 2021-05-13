package com.avioconsulting.opentelemetry.span;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avioconsulting.opentelemetry.OpenTelemetryStarter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
//import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

public class OpenTelemetryMuleSpan {

    private Logger logger = LoggerFactory.getLogger(OpenTelemetryMuleSpan.class);
    private OpenTelemetry openTelemetry; 
    private static String instrumentationName = "instrumentation-library-name";
    public OpenTelemetryMuleSpan() {

        logger.debug("Initialising OpenTelemetry Mule 4 Agent");
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
  //              .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().build()).build())
                .build();

        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }
    
    public void startSpan( ) {
    	if (openTelemetry == null) {
    		openTelemetry = OpenTelemetryStarter.getOpenTelemetry();
    		 SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();
    		     				   
    	}
    	Tracer tracer =
    		    openTelemetry.getTracer("instrumentation-library-name", "1.0.0");
    	Span span = tracer.spanBuilder("my span").startSpan();
    	// put the span into the current Context
    	try (Scope scope = span.makeCurrent()) {
    		// your use case
    	    Thread.sleep(1000);
    	} catch (Throwable t) {
    	    span.setStatus(StatusCode.ERROR, "Change it to your error message");
    	} finally {
    	    span.end(); // closing the scope does not end the span, this has to be done manually
    	}
    }
   
 
}
