/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.source.cockroachdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.airbyte.commons.io.IOs;
import io.airbyte.commons.json.Jsons;
import io.airbyte.commons.string.Strings;
import io.airbyte.commons.util.MoreIterators;
import io.airbyte.integrations.source.jdbc.AbstractJdbcSource;
import io.airbyte.integrations.source.jdbc.SourceJdbcUtils;
import io.airbyte.integrations.source.jdbc.test.JdbcSourceAcceptanceTest;
import io.airbyte.integrations.source.relationaldb.models.DbState;
import io.airbyte.integrations.source.relationaldb.models.DbStreamState;
import io.airbyte.protocol.models.AirbyteCatalog;
import io.airbyte.protocol.models.AirbyteConnectionStatus;
import io.airbyte.protocol.models.AirbyteConnectionStatus.Status;
import io.airbyte.protocol.models.AirbyteMessage;
import io.airbyte.protocol.models.AirbyteMessage.Type;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import io.airbyte.protocol.models.AirbyteStateMessage;
import io.airbyte.protocol.models.AirbyteStream;
import io.airbyte.protocol.models.CatalogHelpers;
import io.airbyte.protocol.models.ConfiguredAirbyteCatalog;
import io.airbyte.protocol.models.ConfiguredAirbyteStream;
import io.airbyte.protocol.models.DestinationSyncMode;
import io.airbyte.protocol.models.Field;
import io.airbyte.protocol.models.JsonSchemaPrimitive;
import io.airbyte.protocol.models.SyncMode;
import io.airbyte.test.utils.CockroachDBContainerHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.utility.MountableFile;

class CockroachDbJdbcSourceAcceptanceTest extends JdbcSourceAcceptanceTest {

  private static CockroachContainer PSQL_DB;
  public static String COL_ROW_ID = "rowid";

  public static Long ID_VALUE_1 = 1L;
  public static Long ID_VALUE_2 = 2L;
  public static Long ID_VALUE_3 = 3L;
  public static Long ID_VALUE_4 = 4L;
  public static Long ID_VALUE_5 = 5L;

  private JsonNode config;

  @BeforeAll
  static void init() {
    PSQL_DB = new CockroachContainer("cockroachdb/cockroach");
    PSQL_DB.start();
  }

  @BeforeEach
  public void setup() throws Exception {
    final String dbName = Strings.addRandomSuffix("db", "_", 10).toLowerCase();

    config = Jsons.jsonNode(ImmutableMap.builder()
        .put("host", PSQL_DB.getHost())
        .put("port", PSQL_DB.getFirstMappedPort() - 1)
        .put("database", dbName)
        .put("username", PSQL_DB.getUsername())
        .put("password", PSQL_DB.getPassword())
        .put("ssl", false)
        .build());

    final String initScriptName = "init_" + dbName.concat(".sql");
    final String tmpFilePath = IOs
        .writeFileToRandomTmpDir(initScriptName, "CREATE DATABASE " + dbName + ";");
    CockroachDBContainerHelper.runSqlScript(MountableFile.forHostPath(tmpFilePath), PSQL_DB);

    super.setup();
  }

  @Override
  public boolean supportsSchemas() {
    return true;
  }

  @Override
  public AbstractJdbcSource getJdbcSource() {
    return new CockroachDbSource();
  }

  @Override
  public JsonNode getConfig() {
    return config;
  }

  @Override
  public String getDriverClass() {
    return CockroachDbSource.DRIVER_CLASS;
  }

  @AfterAll
  static void cleanUp() {
    PSQL_DB.close();
  }

  @Override
  protected AirbyteCatalog getCatalog(final String defaultNamespace) {
    return new AirbyteCatalog().withStreams(Lists.newArrayList(
        CatalogHelpers.createAirbyteStream(
            TABLE_NAME,
            defaultNamespace,
            Field.of(COL_ID, JsonSchemaPrimitive.NUMBER),
            Field.of(COL_NAME, JsonSchemaPrimitive.STRING),
            Field.of(COL_UPDATED_AT, JsonSchemaPrimitive.STRING))
            .withSupportedSyncModes(Lists.newArrayList(SyncMode.FULL_REFRESH, SyncMode.INCREMENTAL))
            .withSourceDefinedPrimaryKey(List.of(List.of(COL_ID))),
        CatalogHelpers.createAirbyteStream(
            TABLE_NAME_WITHOUT_PK,
            defaultNamespace,
            Field.of(COL_ID, JsonSchemaPrimitive.NUMBER),
            Field.of(COL_NAME, JsonSchemaPrimitive.STRING),
            Field.of(COL_UPDATED_AT, JsonSchemaPrimitive.STRING),
            Field.of(COL_ROW_ID, JsonSchemaPrimitive.NUMBER))
            .withSupportedSyncModes(Lists.newArrayList(SyncMode.FULL_REFRESH, SyncMode.INCREMENTAL))
            .withSourceDefinedPrimaryKey(List.of(List.of(COL_ROW_ID))),
        CatalogHelpers.createAirbyteStream(
            TABLE_NAME_COMPOSITE_PK,
            defaultNamespace,
            Field.of(COL_FIRST_NAME, JsonSchemaPrimitive.STRING),
            Field.of(COL_LAST_NAME, JsonSchemaPrimitive.STRING),
            Field.of(COL_UPDATED_AT, JsonSchemaPrimitive.STRING))
            .withSupportedSyncModes(Lists.newArrayList(SyncMode.FULL_REFRESH, SyncMode.INCREMENTAL))
            .withSourceDefinedPrimaryKey(
                List.of(List.of(COL_FIRST_NAME), List.of(COL_LAST_NAME)))));
  }

  @Override
  protected List<AirbyteMessage> getTestMessages() {
    return Lists.newArrayList(
        new AirbyteMessage().withType(Type.RECORD)
            .withRecord(new AirbyteRecordMessage().withStream(streamName)
                .withNamespace(getDefaultNamespace())
                .withData(Jsons.jsonNode(ImmutableMap
                    .of(COL_ID, ID_VALUE_1,
                        COL_NAME, "picard",
                        COL_UPDATED_AT, "2004-10-19T00:00:00Z")))),
        new AirbyteMessage().withType(Type.RECORD)
            .withRecord(new AirbyteRecordMessage().withStream(streamName)
                .withNamespace(getDefaultNamespace())
                .withData(Jsons.jsonNode(ImmutableMap
                    .of(COL_ID, ID_VALUE_2,
                        COL_NAME, "crusher",
                        COL_UPDATED_AT,
                        "2005-10-19T00:00:00Z")))),
        new AirbyteMessage().withType(Type.RECORD)
            .withRecord(new AirbyteRecordMessage().withStream(streamName)
                .withNamespace(getDefaultNamespace())
                .withData(Jsons.jsonNode(ImmutableMap
                    .of(COL_ID, ID_VALUE_3,
                        COL_NAME, "vash",
                        COL_UPDATED_AT, "2006-10-19T00:00:00Z")))));
  }

  @Test
  void testCheckFailure() throws Exception {
    ((ObjectNode) config).put("password", "fake");
    ((ObjectNode) config).put("username", "fake");
    final AirbyteConnectionStatus actual = source.check(config);
    assertEquals(Status.FAILED, actual.getStatus());
  }

  @Test
  void testReadOneColumn() throws Exception {
    final ConfiguredAirbyteCatalog catalog = CatalogHelpers
        .createConfiguredAirbyteCatalog(streamName, getDefaultNamespace(),
            Field.of(COL_ID, JsonSchemaPrimitive.NUMBER));
    final List<AirbyteMessage> actualMessages = MoreIterators
        .toList(source.read(config, catalog, null));

    setEmittedAtToNull(actualMessages);

    final List<AirbyteMessage> expectedMessages = getTestMessages().stream()
        .map(Jsons::clone)
        .peek(m -> {
          ((ObjectNode) m.getRecord().getData()).remove(COL_NAME);
          ((ObjectNode) m.getRecord().getData()).remove(COL_UPDATED_AT);
          ((ObjectNode) m.getRecord().getData()).replace(COL_ID,
              Jsons.jsonNode(m.getRecord().getData().get(COL_ID).asLong()));
        })
        .collect(Collectors.toList());
    assertTrue(expectedMessages.size() == actualMessages.size());
    assertTrue(expectedMessages.containsAll(actualMessages));
    assertTrue(actualMessages.containsAll(expectedMessages));
  }

  @Test
  void testTablesWithQuoting() throws Exception {
    final ConfiguredAirbyteStream streamForTableWithSpaces = createTableWithSpaces();

    final ConfiguredAirbyteCatalog catalog = new ConfiguredAirbyteCatalog()
        .withStreams(Lists.newArrayList(
            getConfiguredCatalogWithOneStream(getDefaultNamespace()).getStreams().get(0),
            streamForTableWithSpaces));
    final List<AirbyteMessage> actualMessages = MoreIterators
        .toList(source.read(config, catalog, null));

    setEmittedAtToNull(actualMessages);

    final List<AirbyteMessage> secondStreamExpectedMessages = getTestMessages()
        .stream()
        .map(Jsons::clone)
        .peek(m -> {
          m.getRecord().setStream(streamForTableWithSpaces.getStream().getName());
          ((ObjectNode) m.getRecord().getData()).set(COL_LAST_NAME_WITH_SPACE,
              ((ObjectNode) m.getRecord().getData()).remove(COL_NAME));
          ((ObjectNode) m.getRecord().getData()).remove(COL_UPDATED_AT);
          ((ObjectNode) m.getRecord().getData()).replace(COL_ID,
              Jsons.jsonNode(m.getRecord().getData().get(COL_ID).asLong()));
        })
        .collect(Collectors.toList());
    final List<AirbyteMessage> expectedMessages = new ArrayList<>(getTestMessages());
    expectedMessages.addAll(secondStreamExpectedMessages);

    assertTrue(expectedMessages.size() == actualMessages.size());
    assertTrue(expectedMessages.containsAll(actualMessages));
    assertTrue(actualMessages.containsAll(expectedMessages));
  }

  @Test
  void testReadOneTableIncrementallyTwice() throws Exception {
    final String namespace = getDefaultNamespace();
    final ConfiguredAirbyteCatalog configuredCatalog = getConfiguredCatalogWithOneStream(namespace);
    configuredCatalog.getStreams().forEach(airbyteStream -> {
      airbyteStream.setSyncMode(SyncMode.INCREMENTAL);
      airbyteStream.setCursorField(Lists.newArrayList(COL_ID));
      airbyteStream.setDestinationSyncMode(DestinationSyncMode.APPEND);
    });

    final DbState state = new DbState()
        .withStreams(Lists.newArrayList(
            new DbStreamState().withStreamName(streamName).withStreamNamespace(namespace)));
    final List<AirbyteMessage> actualMessagesFirstSync = MoreIterators
        .toList(source.read(config, configuredCatalog, Jsons.jsonNode(state)));

    final Optional<AirbyteMessage> stateAfterFirstSyncOptional = actualMessagesFirstSync.stream()
        .filter(r -> r.getType() == Type.STATE).findFirst();
    assertTrue(stateAfterFirstSyncOptional.isPresent());

    database.execute(connection -> {
      connection.createStatement().execute(
          String.format("INSERT INTO %s(id, name, updated_at) VALUES (4,'riker', '2006-10-19')",
              getFullyQualifiedTableName(TABLE_NAME)));
      connection.createStatement().execute(
          String.format("INSERT INTO %s(id, name, updated_at) VALUES (5, 'data', '2006-10-19')",
              getFullyQualifiedTableName(TABLE_NAME)));
    });

    final List<AirbyteMessage> actualMessagesSecondSync = MoreIterators
        .toList(source.read(config, configuredCatalog,
            stateAfterFirstSyncOptional.get().getState().getData()));

    assertEquals(2,
        (int) actualMessagesSecondSync.stream().filter(r -> r.getType() == Type.RECORD).count());
    final List<AirbyteMessage> expectedMessages = new ArrayList<>();
    expectedMessages.add(new AirbyteMessage().withType(Type.RECORD)
        .withRecord(new AirbyteRecordMessage().withStream(streamName).withNamespace(namespace)
            .withData(Jsons.jsonNode(ImmutableMap
                .of(COL_ID, ID_VALUE_4,
                    COL_NAME, "riker",
                    COL_UPDATED_AT, "2006-10-19T00:00:00Z")))));
    expectedMessages.add(new AirbyteMessage().withType(Type.RECORD)
        .withRecord(new AirbyteRecordMessage().withStream(streamName).withNamespace(namespace)
            .withData(Jsons.jsonNode(ImmutableMap
                .of(COL_ID, ID_VALUE_5,
                    COL_NAME, "data",
                    COL_UPDATED_AT, "2006-10-19T00:00:00Z")))));
    expectedMessages.add(new AirbyteMessage()
        .withType(Type.STATE)
        .withState(new AirbyteStateMessage()
            .withData(Jsons.jsonNode(new DbState()
                .withCdc(false)
                .withStreams(Lists.newArrayList(new DbStreamState()
                    .withStreamName(streamName)
                    .withStreamNamespace(namespace)
                    .withCursorField(ImmutableList.of(COL_ID))
                    .withCursor("5")))))));

    setEmittedAtToNull(actualMessagesSecondSync);

    assertTrue(expectedMessages.size() == actualMessagesSecondSync.size());
    assertTrue(expectedMessages.containsAll(actualMessagesSecondSync));
    assertTrue(actualMessagesSecondSync.containsAll(expectedMessages));
  }

  @Test
  void testReadMultipleTables() throws Exception {
    final ConfiguredAirbyteCatalog catalog = getConfiguredCatalogWithOneStream(
        getDefaultNamespace());
    final List<AirbyteMessage> expectedMessages = new ArrayList<>(getTestMessages());

    for (int i = 2; i < 10; i++) {
      final int iFinal = i;
      final String streamName2 = streamName + i;
      database.execute(connection -> {
        connection.createStatement()
            .execute(
                createTableQuery(getFullyQualifiedTableName(TABLE_NAME + iFinal),
                    "id INTEGER, name VARCHAR(200)", ""));
        connection.createStatement()
            .execute(String.format("INSERT INTO %s(id, name) VALUES (1,'picard')",
                getFullyQualifiedTableName(TABLE_NAME + iFinal)));
        connection.createStatement()
            .execute(String.format("INSERT INTO %s(id, name) VALUES (2, 'crusher')",
                getFullyQualifiedTableName(TABLE_NAME + iFinal)));
        connection.createStatement()
            .execute(String.format("INSERT INTO %s(id, name) VALUES (3, 'vash')",
                getFullyQualifiedTableName(TABLE_NAME + iFinal)));
      });
      catalog.getStreams().add(CatalogHelpers.createConfiguredAirbyteStream(
          streamName2,
          getDefaultNamespace(),
          Field.of(COL_ID, JsonSchemaPrimitive.NUMBER),
          Field.of(COL_NAME, JsonSchemaPrimitive.STRING)));

      final List<AirbyteMessage> secondStreamExpectedMessages = getTestMessages()
          .stream()
          .map(Jsons::clone)
          .peek(m -> {
            m.getRecord().setStream(streamName2);
            m.getRecord().setNamespace(getDefaultNamespace());
            ((ObjectNode) m.getRecord().getData()).remove(COL_UPDATED_AT);
            ((ObjectNode) m.getRecord().getData()).replace(COL_ID,
                Jsons.jsonNode(m.getRecord().getData().get(COL_ID).asLong()));
          })
          .collect(Collectors.toList());
      expectedMessages.addAll(secondStreamExpectedMessages);
    }

    final List<AirbyteMessage> actualMessages = MoreIterators
        .toList(source.read(config, catalog, null));

    setEmittedAtToNull(actualMessages);

    assertTrue(expectedMessages.size() == actualMessages.size());
    assertTrue(expectedMessages.containsAll(actualMessages));
    assertTrue(actualMessages.containsAll(expectedMessages));
  }

  @Test
  void testReadMultipleTablesIncrementally() throws Exception {
    final String tableName2 = TABLE_NAME + 2;
    final String streamName2 = streamName + 2;
    database.execute(ctx -> {
      ctx.createStatement().execute(
          createTableQuery(getFullyQualifiedTableName(tableName2), "id INTEGER, name VARCHAR(200)",
              ""));
      ctx.createStatement().execute(
          String.format("INSERT INTO %s(id, name) VALUES (1,'picard')",
              getFullyQualifiedTableName(tableName2)));
      ctx.createStatement().execute(
          String.format("INSERT INTO %s(id, name) VALUES (2, 'crusher')",
              getFullyQualifiedTableName(tableName2)));
      ctx.createStatement().execute(
          String.format("INSERT INTO %s(id, name) VALUES (3, 'vash')",
              getFullyQualifiedTableName(tableName2)));
    });

    final String namespace = getDefaultNamespace();
    final ConfiguredAirbyteCatalog configuredCatalog = getConfiguredCatalogWithOneStream(
        namespace);
    configuredCatalog.getStreams().add(CatalogHelpers.createConfiguredAirbyteStream(
        streamName2,
        namespace,
        Field.of(COL_ID, JsonSchemaPrimitive.NUMBER),
        Field.of(COL_NAME, JsonSchemaPrimitive.STRING)));
    configuredCatalog.getStreams().forEach(airbyteStream -> {
      airbyteStream.setSyncMode(SyncMode.INCREMENTAL);
      airbyteStream.setCursorField(Lists.newArrayList(COL_ID));
      airbyteStream.setDestinationSyncMode(DestinationSyncMode.APPEND);
    });

    final DbState state = new DbState()
        .withStreams(Lists.newArrayList(
            new DbStreamState().withStreamName(streamName).withStreamNamespace(namespace)));
    final List<AirbyteMessage> actualMessagesFirstSync = MoreIterators
        .toList(source.read(config, configuredCatalog, Jsons.jsonNode(state)));

    // get last state message.
    final Optional<AirbyteMessage> stateAfterFirstSyncOptional = actualMessagesFirstSync.stream()
        .filter(r -> r.getType() == Type.STATE)
        .reduce((first, second) -> second);
    assertTrue(stateAfterFirstSyncOptional.isPresent());

    // we know the second streams messages are the same as the first minus the updated at column. so we
    // cheat and generate the expected messages off of the first expected messages.
    final List<AirbyteMessage> secondStreamExpectedMessages = getTestMessages()
        .stream()
        .map(Jsons::clone)
        .peek(m -> {
          m.getRecord().setStream(streamName2);
          ((ObjectNode) m.getRecord().getData()).remove(COL_UPDATED_AT);
          ((ObjectNode) m.getRecord().getData()).replace(COL_ID,
              Jsons.jsonNode(m.getRecord().getData().get(COL_ID).asLong()));
        })
        .collect(Collectors.toList());
    final List<AirbyteMessage> expectedMessagesFirstSync = new ArrayList<>(getTestMessages());
    expectedMessagesFirstSync.add(new AirbyteMessage()
        .withType(Type.STATE)
        .withState(new AirbyteStateMessage()
            .withData(Jsons.jsonNode(new DbState()
                .withCdc(false)
                .withStreams(Lists.newArrayList(
                    new DbStreamState()
                        .withStreamName(streamName)
                        .withStreamNamespace(namespace)
                        .withCursorField(ImmutableList.of(COL_ID))
                        .withCursor("3"),
                    new DbStreamState()
                        .withStreamName(streamName2)
                        .withStreamNamespace(namespace)
                        .withCursorField(ImmutableList.of(COL_ID))))))));

    expectedMessagesFirstSync.addAll(secondStreamExpectedMessages);
    expectedMessagesFirstSync.add(new AirbyteMessage()
        .withType(Type.STATE)
        .withState(new AirbyteStateMessage()
            .withData(Jsons.jsonNode(new DbState()
                .withCdc(false)
                .withStreams(Lists.newArrayList(
                    new DbStreamState()
                        .withStreamName(streamName)
                        .withStreamNamespace(namespace)
                        .withCursorField(ImmutableList.of(COL_ID))
                        .withCursor("3"),
                    new DbStreamState()
                        .withStreamName(streamName2)
                        .withStreamNamespace(namespace)
                        .withCursorField(ImmutableList.of(COL_ID))
                        .withCursor("3")))))));

    setEmittedAtToNull(actualMessagesFirstSync);

    assertTrue(expectedMessagesFirstSync.size() == actualMessagesFirstSync.size());
    assertTrue(expectedMessagesFirstSync.containsAll(actualMessagesFirstSync));
    assertTrue(actualMessagesFirstSync.containsAll(expectedMessagesFirstSync));
  }

  @Test
  void testDiscoverWithMultipleSchemas() throws Exception {
    // clickhouse and mysql do not have a concept of schemas, so this test does not make sense for them.
    if (getDriverClass().toLowerCase().contains("mysql") || getDriverClass().toLowerCase()
        .contains("clickhouse")) {
      return;
    }

    // add table and data to a separate schema.
    database.execute(connection -> {
      connection.createStatement().execute(
          String.format("CREATE TABLE %s(id VARCHAR(200), name VARCHAR(200))",
              SourceJdbcUtils.getFullyQualifiedTableName(SCHEMA_NAME2, TABLE_NAME)));
      connection.createStatement()
          .execute(String.format("INSERT INTO %s(id, name) VALUES ('1','picard')",
              SourceJdbcUtils.getFullyQualifiedTableName(SCHEMA_NAME2, TABLE_NAME)));
      connection.createStatement()
          .execute(String.format("INSERT INTO %s(id, name) VALUES ('2', 'crusher')",
              SourceJdbcUtils.getFullyQualifiedTableName(SCHEMA_NAME2, TABLE_NAME)));
      connection.createStatement()
          .execute(String.format("INSERT INTO %s(id, name) VALUES ('3', 'vash')",
              SourceJdbcUtils.getFullyQualifiedTableName(SCHEMA_NAME2, TABLE_NAME)));
    });

    final AirbyteCatalog actual = source.discover(config);

    final AirbyteCatalog expected = getCatalog(getDefaultNamespace());
    expected.getStreams().add(CatalogHelpers
        .createAirbyteStream(TABLE_NAME,
            SCHEMA_NAME2,
            Field.of(COL_ID, JsonSchemaPrimitive.STRING),
            Field.of(COL_NAME, JsonSchemaPrimitive.STRING),
            Field.of(COL_ROW_ID, JsonSchemaPrimitive.NUMBER))
        .withSupportedSyncModes(Lists.newArrayList(SyncMode.FULL_REFRESH, SyncMode.INCREMENTAL))
        .withSourceDefinedPrimaryKey(List.of(List.of(COL_ROW_ID))));

    // sort streams by name so that we are comparing lists with the same order.
    final Comparator<AirbyteStream> schemaTableCompare = Comparator
        .comparing(stream -> stream.getNamespace() + "." + stream.getName());
    expected.getStreams().sort(schemaTableCompare);
    actual.getStreams().sort(schemaTableCompare);
    assertEquals(expected, filterOutOtherSchemas(actual));
  }

}
