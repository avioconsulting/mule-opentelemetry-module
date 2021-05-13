package ARCHIVE.co.elastic.apm.mule4.agent;


import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ARCHIVE.co.elastic.apm.mule4.agent.config.BaseAbstractApmMuleTestCase;

public class SimpleFlowTest extends BaseAbstractApmMuleTestCase {

	@Test
	public void testSimpleFlow() throws Exception {
		flowRunner("dep-testFlow").run();
		
		Thread.sleep(1000);

		assertEquals("dep-testFlow", getTransaction().getNameAsString());
		assertEquals(6, getSpans().size());
		assertEquals("Logger1", getSpans().get(0).getNameAsString());
		assertEquals("Logger3", getSpans().get(1).getNameAsString());
		assertEquals("Logger4", getSpans().get(2).getNameAsString());
		assertEquals("Flow Reference2", getSpans().get(3).getNameAsString());
		assertEquals("Flow Reference1", getSpans().get(4).getNameAsString());
		assertEquals("Logger2", getSpans().get(5).getNameAsString());
	}

	@Override
	protected String getConfigFile() {
		return "SimpleFlowTest.xml";
	}
	
}
