package ARCHIVE.co.elastic.apm.mule4.agent.config;

import static org.mockito.Mockito.spy;

import java.util.ServiceLoader;

import org.mockito.Mockito;

import co.elastic.apm.agent.shaded.stagemonitor.configuration.ConfigurationOptionProvider;
import co.elastic.apm.agent.shaded.stagemonitor.configuration.ConfigurationRegistry;
import co.elastic.apm.agent.shaded.stagemonitor.configuration.source.SimpleSource;

public class SpyConfiguration {

	public static final String CONFIG_SOURCE_NAME = "test config source";

	public static ConfigurationRegistry createSpyConfig() {
		ConfigurationRegistry.Builder builder = ConfigurationRegistry.builder();
		for (ConfigurationOptionProvider options : ServiceLoader.load(ConfigurationOptionProvider.class)) {
			builder.addOptionProvider(spy(options));
		}
		return builder.addConfigSource(new SimpleSource(CONFIG_SOURCE_NAME).add("log_level", "INFO")
				.add("instrument", "false")
				.add("server_urls", "")).build();
	}

	public static void reset(ConfigurationRegistry config) {
		for (ConfigurationOptionProvider provider : config.getConfigurationOptionProviders()) {
			Mockito.reset(provider);
		}
	}
}
