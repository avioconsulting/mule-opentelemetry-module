package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import org.mule.runtime.api.interception.SourceInterceptor;
import org.mule.runtime.api.interception.SourceInterceptorFactory;

public class TestSourceInterceptorFactory implements SourceInterceptorFactory {
    @Override
    public SourceInterceptor get() {
        return new TestSourceInterceptor();
    }
}
