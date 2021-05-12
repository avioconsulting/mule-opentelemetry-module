package ARCHIVE.co.elastic.apm.mule4.agent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ARCHIVE.co.elastic.apm.mule4.agent.config.BaseAbstractApmMuleTestCase;

public class ErrorFlowTest extends BaseAbstractApmMuleTestCase {

	@Test
	public void testSimpleFlow() throws Exception {
		flowRunner("ErrorFlowTest").withSourceCorrelationId("123").runExpectingException();

		Thread.sleep(1000);

		assertEquals("ErrorFlowTest", getTransaction().getNameAsString());
		assertEquals(1, getSpans().size());
		assertEquals(1, getErrors().size());
		assertEquals("Execute", getSpans().get(0).getNameAsString());
		assertEquals("java.lang.Exception: This is an error", getErrors().get(0).getException().getMessage());

	}

	@Override
	protected String getConfigFile() {
		return "ErrorFlowTest.xml";
	}

}
