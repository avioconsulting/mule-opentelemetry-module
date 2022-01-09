package com.avioconsulting.mule.opentelemetry.store;

import java.io.Serializable;

public class Transaction implements Serializable {
    private final String transactionId;
    private final String rootFlowName;
    private final FlowSpan rootFlowSpan;

    public Transaction(String transactionId, String rootFlowName, FlowSpan rootFlowSpan) {
        this.transactionId = transactionId;
        this.rootFlowName = rootFlowName;
        this.rootFlowSpan = rootFlowSpan;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getRootFlowName() {
        return rootFlowName;
    }

    public FlowSpan getRootFlowSpan() {
        return rootFlowSpan;
    }

}
