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
  @Parameters({ "generic-connection, other_sql, database",
      "my-sql-connection, mysql, database",
      "mssql-connection, mssql, databaseName",
      "oracle-connection, oracle, serviceName",
      "data-source-connection, other_sql, dataSourceRef" })
  public void testDBProcessorTagExtraction(String connectionType, String expectedDbSysName, String dbNameKey) {

    ConfigurationComponentLocator componentLocator = mock(ConfigurationComponentLocator.class);

    // Generic DB System
    ComponentIdentifier identifier = getMockedIdentifier("db", connectionType);
    ComponentLocation configComponentLocation = getComponentLocation();
    Map<String, String> connectionConfig = new HashMap<>();
    connectionConfig.put(dbNameKey, "testDb");
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
    config.put("sql", "select * from test");
    config.put("config-ref", "Database_Config");
    Component component = getComponent(componentLocation, config, "db", "select");

    Map<String, String> attributes = dbProcessorComponent.getAttributes(component, null);

    assertThat(attributes)
        .containsEntry("db.system", expectedDbSysName)
        .containsEntry("net.peer.name", "localhost")
        .containsEntry("net.peer.port", "2004")
        .containsEntry("db.user", "test")
        .containsEntry("db.mssql.instance_name", "testInstanceName")
        .containsEntry("db.oracle.instance", "testInstance")
        .containsEntry("db.jdbc.driver_classname", "testDriverClassName");

    if (dbNameKey.equalsIgnoreCase("serviceName")) {
      assertThat(attributes)
          .containsEntry("db.oracle.serviceName", "testDb");
    } else if (dbNameKey.equalsIgnoreCase("dataSourceRef")) {
      assertThat(attributes)
          .containsEntry("db.datasource", "testDb");
    } else {
      assertThat(attributes)
          .containsEntry("db.name", "testDb");
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
        .containsEntry("db.statement", "select * from test")
        .containsEntry("db.operation", "select");
  }
}