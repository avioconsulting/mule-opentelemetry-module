package ARCHIVE.co.elastic.apm.mule4.agent;

import static org.junit.Assert.*;

import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import co.elastic.apm.agent.impl.transaction.Transaction;
import ARCHIVE.co.elastic.apm.mule4.agent.config.BaseAbstractApmMuleTestCase;
import ARCHIVE.co.elastic.apm.mule4.agent.tracing.HttpTracingUtils;

public class DistributedTracingTest extends BaseAbstractApmMuleTestCase {

	private static final String TRACE_ID1 = "0af7651916cd43dd8448eb211c80319c";
	private static final String TRACE_PARENT1 = "00-" + TRACE_ID1 + "-b9c7c989f97918e1-01";

	@Test
	public void testTraceIdPropagation() throws Exception {

		HttpGet getRequest = new HttpGet("http://localhost:8998/request");
		getRequest.addHeader(HttpTracingUtils.ELASTIC_APM_TRACEPARENT_HEADER, TRACE_PARENT1);

		HttpClient httpClient = HttpClientBuilder.create().build();

		HttpResponse response = httpClient.execute(getRequest);

		assertEquals(200, response.getStatusLine().getStatusCode());
		assertEquals(TRACE_PARENT1, EntityUtils.toString(response.getEntity()));
		List<Transaction> transactions = getTransactions();
		assertEquals(1, transactions.size());
		assertEquals(1, getSpans().size());
		assertEquals(TRACE_ID1, getTransaction().getTraceContext().getTraceId().toString());

	}
	
	@Test
	public void testNoTraceIdPropagation() throws Exception {

		HttpGet getRequest = new HttpGet("http://localhost:8998/request");

		HttpClient httpClient = HttpClientBuilder.create().build();

		HttpResponse response = httpClient.execute(getRequest);

		Thread.sleep(1000);
		
		assertEquals(200, response.getStatusLine().getStatusCode());
		assertEquals("", EntityUtils.toString(response.getEntity()));
		List<Transaction> transactions = getTransactions();
		assertEquals(1, transactions.size());
		assertEquals(1, getSpans().size());
		assertNotEquals(TRACE_ID1, getTransaction().getTraceContext().getTraceId().toString());
		assertNotEquals("", getTransaction().getTraceContext().getTraceId().toString());

	}

	@Test
	public void testTraceIdPropagationThroughHttp() throws Exception {

		HttpGet getRequest = new HttpGet("http://localhost:8998/traceparentrequest");
		getRequest.addHeader(HttpTracingUtils.TRACEPARENT_HEADER, TRACE_PARENT1);

		HttpClient httpClient = HttpClientBuilder.create().build();

		HttpResponse response = httpClient.execute(getRequest);
		
		Thread.sleep(1000);

		assertEquals(200, response.getStatusLine().getStatusCode());
		assertEquals(TRACE_PARENT1, EntityUtils.toString(response.getEntity()));
		List<Transaction> transactions = getTransactions();
		assertEquals(1, transactions.size());
		assertEquals(1, getSpans().size());
		assertEquals(TRACE_ID1, getTransaction().getTraceContext().getTraceId().toString());
	}
	
	@Override
	protected String getConfigFile() {
		return "DistributedTracingTest.xml";
	}

}
