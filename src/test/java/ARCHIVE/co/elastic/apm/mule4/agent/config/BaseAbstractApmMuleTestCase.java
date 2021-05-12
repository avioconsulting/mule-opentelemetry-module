package ARCHIVE.co.elastic.apm.mule4.agent.config;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

import com.google.common.collect.ImmutableList;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.shaded.stagemonitor.configuration.ConfigurationRegistry;
import net.bytebuddy.agent.ByteBuddyAgent;

@ArtifactClassLoaderRunnerConfig(applicationSharedRuntimeLibs = { "co.elastic.apm:elastic-apm-agent",
		"co.elastic.apm:apm-agent-attach", "co.elastic.apm:apm-agent-api", "co.elastic.apm:mule4-agent",
		 })
public abstract class BaseAbstractApmMuleTestCase extends MuleArtifactFunctionalTestCase {

	protected List<Span> spans;
	protected List<Transaction> tx;
	protected List<ErrorCapture> errors;
	private ConfigurationRegistry config;
	private ElasticApmTracer tracer;
	private Reporter reporter;

	@Before
	public void init() {
		spans = new ArrayList<Span>();
		tx = new ArrayList<Transaction>();
		errors = new ArrayList<ErrorCapture>();
	}

	@Override
	public void doSetUpBeforeMuleContextCreation() {

		reporter = Mockito.mock(Reporter.class);

		Mockito.doAnswer(new Answer<Span>() {
			@Override
			public Span answer(InvocationOnMock invocation) throws Throwable {
				Span argument = invocation.getArgument(0, Span.class);
				spans.add(argument);
				return null;
			}
		}).when(reporter).report(Mockito.any(Span.class));

		Mockito.doAnswer(new Answer<Transaction>() {
			@Override
			public Transaction answer(InvocationOnMock invocation) throws Throwable {
				tx.add(invocation.getArgument(0, Transaction.class));
				return null;
			}
		}).when(reporter).report(Mockito.any(Transaction.class));

		Mockito.doAnswer(new Answer<ErrorCapture>() {
			@Override
			public ErrorCapture answer(InvocationOnMock invocation) throws Throwable {
				ErrorCapture argument = invocation.getArgument(0, ErrorCapture.class);
				errors.add(argument);
				return null;
			}
		}).when(reporter).report(Mockito.any(ErrorCapture.class));

		config = SpyConfiguration.createSpyConfig();
		tracer = new ElasticApmTracerBuilder().configurationRegistry(config).reporter(reporter).build();
		tracer.start();

		ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

	}

	@After
	public void tearDown() {
		ElasticApmAgent.reset();
	}

	public List<Span> getSpans() {
		return spans;
	}

	public Transaction getTransaction() {

		Transaction transaction = null;

		synchronized (tx) {
			if (tx.size() > 0)
				transaction = ImmutableList.copyOf(tx).get(0);
		}

		return transaction;
	}

	public List<Transaction> getTransactions() {
		return ImmutableList.copyOf(tx);
	}

	public List<ErrorCapture> getErrors() {
		return errors;
	}

}
