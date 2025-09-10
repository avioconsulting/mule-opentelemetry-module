package com.avioconsulting.mule.opentelemetry.api.traces;

import org.junit.Test;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class TraceComponentTagsTest {

  @Test
  public void addTag() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<>());
    tc.addTag("test-key", "test-value");
    assertThat(tc.getReadOnlyTags()).containsEntry("test-key", "test-value");
  }

  @Test
  public void getTag() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<>());
    tc.addTag("test-key", "test-value");
    assertThat(tc.getReadOnlyTags()).containsEntry("test-key", "test-value");
  }

  @Test
  public void addAllTags() {
    Map<String, String> tags = new HashMap<>();
    tags.put("test-key", "test-value");
    tags.put("test-key-2", "test-value-2");
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addAllTags(tags);
    assertThat(tc.getReadOnlyTags()).containsAllEntriesOf(tags);
  }

  @Test
  public void removeTag() {
    Map<String, String> tags = new HashMap<>();
    tags.put("test-key", "test-value");
    tags.put("test-key-2", "test-value-2");
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addAllTags(tags);
    tc.removeTag("test-key");
    assertThat(tc.getReadOnlyTags()).doesNotContainKey("test-key");
    assertThat(tc.getReadOnlyTags()).containsEntry("test-key-2", "test-value-2");
  }

  @Test
  public void copyTagsTo() {
    Map<String, String> target = new HashMap<>();
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addTag("test-key", "test-value");
    tc.addTag("test-key-2", "test-value-2");
    tc.copyTagsTo(target);
    assertThat(target).containsAllEntriesOf(tc.getReadOnlyTags());
  }

  @Test
  public void testCopyTagsTo() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addTag("test-key", "test-value");
    tc.addTag("test-key-2", "test-value-2");

    TraceComponent targetTC = TraceComponent.of("test2", new HashMap<String, String>());
    tc.copyTagsTo(targetTC);
    assertThat(targetTC.getReadOnlyTags()).containsAllEntriesOf(tc.getReadOnlyTags());
  }

  @Test
  public void testCopyTagsTo1() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addTag("test-key", "test-value");
    tc.addTag("test-key-2", "test-value-2");

    TraceComponent targetTC = TraceComponent.of("test2", new HashMap<String, String>());

    tc.copyTagsTo(targetTC, key -> !key.equals("test-key-2"));

    assertThat(targetTC.getReadOnlyTags()).containsOnly(new AbstractMap.SimpleEntry<>("test-key", "test-value"));
  }

  @Test
  public void hasTagFor() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addTag("test-key", "test-value");
    tc.addTag("test-key-2", "test-value-2");
    assertThat(tc.hasTagFor("test-key")).isTrue();
    assertThat(tc.hasTagFor("test-key-3")).isFalse();
  }

  @Test
  public void hasTags() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addTag("test-key", "test-value");
    tc.addTag("test-key-2", "test-value-2");
    assertThat(tc.hasTags()).isTrue();
    tc.removeTag("test-key");
    tc.removeTag("test-key-2");
    assertThat(tc.hasTags()).isFalse();
  }

  @Test
  public void containsTag() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addTag("test-key", "test-value");
    tc.addTag("test-key-2", "test-value-2");
    assertThat(tc.containsTag("test-key", "test-value")).isTrue();
    assertThat(tc.containsTag("test-key", "wrong")).isFalse();
    assertThat(tc.containsTag("wrong", "test-value")).isFalse();
  }

  @Test
  public void forEachTagEntry_Consume() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addTag("test-key", "test-value");
    tc.addTag("test-key-2", "test-value-2");

    tc.forEachTagEntry(entry -> {
      assertThat(entry.getKey()).isIn("test-key", "test-key-2");
      assertThat(entry.getValue()).isIn("test-value", "test-value-2");
    });
  }

  @Test
  public void forEachTagEntry_Update() {
    TraceComponent tc = TraceComponent.of("test", new HashMap<String, String>());
    tc.addTag("test-key", "test-value");
    tc.addTag("test-key-2", "test-value-2");

    tc.forEachTagEntry(entry -> {
      if (entry.getKey().equals("test-key")) {
        entry.setValue("updated-test-value");
      }
    });

    assertThat(tc.getReadOnlyTags()).containsEntry("test-key", "updated-test-value");
    assertThat(tc.getReadOnlyTags()).containsEntry("test-key-2", "test-value-2");
  }
}