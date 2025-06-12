package com.avioconsulting.mule.opentelemetry.internal.processor;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(JUnitParamsRunner.class)
public class DBProcessorComponentTest extends AbstractProcessorComponentTest {

  @Test
  @Parameters({ "generic-connection, postgresql, database, testdb",
      "my-sql-connection, mysql, database, testDb",
      "mssql-connection, mssql, databaseName, localhost\\testInstanceName/testDb",
      "oracle-connection, oracle, serviceName, localhost:2004/testDb:testInstance",
      "data-source-connection, other_sql, dataSourceRef, testDb" })
  public void testDBProcessorTagExtraction(String connectionType, String expectedDbSysName, String dbNameKey,
      String expectedDBNamespace) {

    ConfigurationComponentLocator componentLocator = mock(ConfigurationComponentLocator.class);

    // Generic DB System
    ComponentIdentifier identifier = getMockedIdentifier("db", connectionType);
    ComponentLocation configComponentLocation = getComponentLocation();
    Map<String, String> connectionConfig = new HashMap<>();
    connectionConfig.put(dbNameKey, "testDb");
    connectionConfig.put("url", "jdbc:postgresql://localhost:2004/testdb");
    connectionConfig.put("host", "localhost");
    connectionConfig.put("port", "2004");
    connectionConfig.put("user", "test");
    connectionConfig.put("password", "test");
    connectionConfig.put("instanceName", "testInstanceName");
    connectionConfig.put("instance", "testInstance");
    connectionConfig.put("driverClassName", "testDriverClassName");
    Component configComponent = getComponent(configComponentLocation, connectionConfig, "db", "config");
    when(configComponent.getIdentifier()).thenReturn(identifier);
    when(componentLocator.find(any(Location.class))).thenReturn(Optional.of(configComponent));

    DBProcessorComponent dbProcessorComponent = new DBProcessorComponent();
    dbProcessorComponent.withConfigurationComponentLocator(componentLocator);
    ComponentLocation componentLocation = getComponentLocation();
    Map<String, String> config = new HashMap<>();
    config.put("sql", "select * from test where id = :id");
    config.put("config-ref", "Database_Config");
    config.put("inputParameters", "#[{id: 1}]");
    Component component = getComponent(componentLocation, config, "db", "select");

    Map<String, String> attributes = dbProcessorComponent.getAttributes(component, null);

    assertThat(attributes)
        .containsEntry("db.system", expectedDbSysName)
        .containsEntry("db.operation.name", "select")
        .containsEntry("db.query.text", "select * from test where id = :id")
        .containsEntry("db.namespace", expectedDBNamespace)
        .containsEntry("inputParameters", "#[{id: 1}]");
    if (!dbNameKey.equalsIgnoreCase("dataSourceRef")) {
      assertThat(attributes)
          .containsEntry("server.address", "localhost")
          .containsEntry("server.port", "2004");
    }
  }

  @Test
  public void testDBProcessorTagExtraction_WhenNoParameters() {

    ConfigurationComponentLocator componentLocator = mock(ConfigurationComponentLocator.class);
    // Cannot find a connection config element using locator
    when(componentLocator.find(any(Location.class))).thenReturn(Optional.empty());

    DBProcessorComponent dbProcessorComponent = new DBProcessorComponent();
    dbProcessorComponent.withConfigurationComponentLocator(componentLocator);
    ComponentLocation componentLocation = getComponentLocation();
    Map<String, String> config = new HashMap<>();
    config.put("sql", "select * from test");
    config.put("config-ref", "Database_Config");
    Component component = getComponent(componentLocation, config, "db", "select");

    Map<String, String> attributes = dbProcessorComponent.getAttributes(component, null);

    assertThat(attributes)
        .containsEntry("db.system", "other_sql")
        .containsEntry("db.query.text", "select * from test")
        .containsEntry("db.operation.name", "select");
  }
}