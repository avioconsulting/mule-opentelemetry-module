package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.store.InMemoryTransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.utils.TraceUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.interception.ProcessorInterceptor;
import org.mule.runtime.api.interception.ProcessorParameterValue;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Map;

@Component
public class ProcessorTracingInterceptor implements ProcessorInterceptor {

    TransactionStore transactionStore = InMemoryTransactionStore.getInstance();

    @Override
    public void before(ComponentLocation location, Map<String, ProcessorParameterValue> parameters, InterceptionEvent event) {
        //TODO: If overall transactionId generation strategy is changed, this needs to be changed.
        String transactionId = transactionStore.transactionIdFor(event);
        Context transactionContext = transactionStore.getTransactionContext(transactionId);
        event.addVariable("TRACE_TRANSACTION_ID", transactionId);
        try(Scope scope = transactionContext.makeCurrent()) {
            TraceUtil.injectTraceContext(event, FlowVarTextMapSetter.INSTANCE);
        }
    }

    public static enum FlowVarTextMapSetter implements TextMapSetter<InterceptionEvent> {
        INSTANCE;

        @Override
        public void set(@Nullable InterceptionEvent carrier, String key, String value) {
            if(carrier != null) carrier.addVariable(key, value);
        }
    }
}
