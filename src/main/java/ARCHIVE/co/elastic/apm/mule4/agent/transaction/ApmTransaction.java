package ARCHIVE.co.elastic.apm.mule4.agent.transaction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import co.elastic.apm.api.HeaderInjector;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;

/*
 * Wrapper class around APM Transaction to allow retrieval of some of the setter-only attributes.
 */
public class ApmTransaction implements Transaction {

	private Transaction tx;
	private String flowName;
	private Map<String, String> labels = new ConcurrentHashMap<String, String>();
	private boolean hasException = false;

	public ApmTransaction(Transaction transaction) {
		this.tx = transaction;
	}

	public Transaction setName(String name) {
		return tx.setName(name);
	}

	public Transaction setType(String type) {
		return tx.setType(type);
	}

	@SuppressWarnings("deprecation")
	public Transaction addTag(String key, String value) {
		return tx.addTag(key, value);
	}

	/*
	 * Custom setter to record custom label settings
	 * @see co.elastic.apm.api.Transaction#addLabel(java.lang.String, java.lang.String)
	 */
	public Transaction addLabel(String key, String value) {

		labels.put(key, value);

		return tx.addLabel(key, value);
	}

	// Return custom set label
	public Optional<String> getLabel(String key) {
		return Optional.ofNullable(labels.get(key));
	}

	public Transaction addLabel(String key, Number value) {
		return tx.addLabel(key, value);
	}

	public Transaction addLabel(String key, boolean value) {
		return tx.addLabel(key, value);
	}

	public Transaction addCustomContext(String key, String value) {
		return tx.addCustomContext(key, value);
	}

	public Transaction addCustomContext(String key, Number value) {
		return tx.addCustomContext(key, value);
	}

	public Transaction addCustomContext(String key, boolean value) {
		return tx.addCustomContext(key, value);
	}

	public Transaction setUser(String id, String email, String username) {
		return tx.setUser(id, email, username);
	}

	public Transaction setResult(String result) {
		return tx.setResult(result);
	}

	public Transaction setStartTimestamp(long epochMicros) {
		return tx.setStartTimestamp(epochMicros);
	}

	public void end() {
		tx.end();
	}

	@SuppressWarnings("deprecation")
	public Span createSpan() {
		return tx.createSpan();
	}

	public String getId() {
		return tx.getId();
	}

	public String ensureParentId() {
		return tx.ensureParentId();
	}

	public Span startSpan(String type, String subtype, String action) {
		return tx.startSpan(type, subtype, action);
	}

	public Scope activate() {
		return tx.activate();
	}

	public Span startSpan() {
		return tx.startSpan();
	}

	public void end(long epochMicros) {
		tx.end(epochMicros);
	}

	public String captureException(Throwable throwable) {
		return tx.captureException(throwable);
	}

	public String getTraceId() {
		return tx.getTraceId();
	}

	public boolean isSampled() {
		return tx.isSampled();
	}

	public void injectTraceHeaders(HeaderInjector headerInjector) {
		tx.injectTraceHeaders(headerInjector);
	}

	/*
	 * Custom getter for the flowName
	 */
	public String getFlowName() {
		return flowName;
	}

	/*
	 * Custom setter for the flowName
	 */
	public void setFlowName(String flowName) {
		this.flowName = flowName;
	}

	public boolean hasException() {
		return this.hasException;
	}
	
	public void setException() {
		this.hasException = true;
	}

}
