package ARCHIVE.co.elastic.apm.mule4.agent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ARCHIVE.co.elastic.apm.mule4.agent.config.BaseAbstractApmMuleTestCase;

public class NestedExceptionFlowTest extends BaseAbstractApmMuleTestCase {

	@Test
	public void testSimpleFlow() throws Exception {
		flowRunner("NestedErrorFlowTestFlow").withSourceCorrelationId("123").runExpectingException();

		Thread.sleep(1000);

		assertEquals("NestedErrorFlowTestFlow", getTransaction().getNameAsString());
		assertEquals(5, getSpans().size());
		assertEquals(1, getErrors().size());
		assertEquals("Logger1", getSpans().get(0).getNameAsString());
		assertEquals("Logger3", getSpans().get(1).getNameAsString());
		assertEquals("Execute", getSpans().get(2).getNameAsString());
		assertEquals("Flow Reference", getSpans().get(3).getNameAsString());
		assertEquals("Flow Reference", getSpans().get(4).getNameAsString());
		assertEquals("java.lang.Exception: This is an error", getErrors().get(0).getException().getMessage());

	}

	@Override
	protected String getConfigFile() {
		return "NestedErrorFlowTest.xml";
	}

}
