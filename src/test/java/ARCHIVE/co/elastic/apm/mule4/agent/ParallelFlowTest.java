package ARCHIVE.co.elastic.apm.mule4.agent;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import co.elastic.apm.agent.impl.transaction.Span;
import ARCHIVE.co.elastic.apm.mule4.agent.config.BaseAbstractApmMuleTestCase;

public class ParallelFlowTest extends BaseAbstractApmMuleTestCase {

	@Test
	public void testSimpleFlow() throws Exception {
		flowRunner("ParallelFlowTest").run();

		Thread.sleep(2000);

		assertEquals("ParallelFlowTest", getTransaction().getNameAsString());

		List<Span> spans2 = getSpans();

		assertEquals(9, spans2.size());
		assertEquals("Logger1", spans2.get(0).getNameAsString());

		String[] array = spans2.subList(1, 6).stream().map(x -> x.getNameAsString()).sorted()
				.collect(Collectors.toSet()).toArray(new String[0]);

//		assertArrayEquals(Arrays.asList("Logger21", "Logger22", "Logger23", "Logger31", "Logger32").toArray(new String[0]), array);

		assertEquals(array.length, 5);
		assertEquals("Scatter-Gather", spans2.get(7).getNameAsString());
		assertEquals("Logger5", spans2.get(8).getNameAsString());
	}

	@Override
	protected String getConfigFile() {
		return "ParallelFlowTest.xml";
	}

}
