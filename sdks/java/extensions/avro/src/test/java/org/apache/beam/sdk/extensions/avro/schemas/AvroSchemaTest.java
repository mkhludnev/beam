/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.extensions.avro.schemas;

import static org.apache.beam.sdk.schemas.utils.SchemaTestUtils.equivalentTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.avro.Conversions;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.reflect.AvroIgnore;
import org.apache.avro.reflect.AvroName;
import org.apache.avro.reflect.AvroSchema;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.beam.sdk.extensions.avro.schemas.utils.AvroUtils;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.Schema.FieldType;
import org.apache.beam.sdk.schemas.logicaltypes.EnumerationType;
import org.apache.beam.sdk.schemas.logicaltypes.FixedBytes;
import org.apache.beam.sdk.schemas.logicaltypes.FixedPrecisionNumeric;
import org.apache.beam.sdk.schemas.transforms.Group;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.testing.ValidatesRunner;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.apache.beam.sdk.util.SerializableUtils;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.collect.ImmutableList;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Tests for AVRO schema classes. */
public class AvroSchemaTest {
  /** A test POJO that corresponds to our AVRO schema. */
  public static class AvroSubPojo {
    @AvroName("BOOL_NON_NULLABLE")
    public boolean boolNonNullable;

    @AvroName("int")
    @org.apache.avro.reflect.Nullable
    public Integer anInt;

    public AvroSubPojo(boolean boolNonNullable, Integer anInt) {
      this.boolNonNullable = boolNonNullable;
      this.anInt = anInt;
    }

    public AvroSubPojo() {}

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AvroSubPojo)) {
        return false;
      }
      AvroSubPojo that = (AvroSubPojo) o;
      return boolNonNullable == that.boolNonNullable && Objects.equals(anInt, that.anInt);
    }

    @Override
    public int hashCode() {
      return Objects.hash(boolNonNullable, anInt);
    }

    @Override
    public String toString() {
      return "AvroSubPojo{" + "boolNonNullable=" + boolNonNullable + ", anInt=" + anInt + '}';
    }
  }

  /** A test POJO that corresponds to our AVRO schema. */
  public static class AvroPojo {
    public @AvroName("bool_non_nullable") boolean boolNonNullable;

    @org.apache.avro.reflect.Nullable
    public @AvroName("int") Integer anInt;

    @org.apache.avro.reflect.Nullable
    public @AvroName("short") Integer aShort;

    @org.apache.avro.reflect.Nullable
    public @AvroName("long") Long aLong;

    @AvroName("float")
    @org.apache.avro.reflect.Nullable
    public Float aFloat;

    @AvroName("double")
    @org.apache.avro.reflect.Nullable
    public Double aDouble;

    @org.apache.avro.reflect.Nullable public String string;
    @org.apache.avro.reflect.Nullable public ByteBuffer bytes;

    @AvroSchema("{\"type\": \"fixed\", \"size\": 4, \"name\": \"fixed4\"}")
    public byte[] fixed;

    // @org.apache.avro.reflect.Nullable
    @AvroSchema(
        "[\"null\",{\"type\":\"bytes\", \"logicalType\": \"decimal\",\"precision\":10,\"scale\":2}]")
    public BigDecimal decimalScale;

    @AvroSchema("{\"type\": \"int\", \"logicalType\": \"date\"}")
    public LocalDate date;

    @AvroSchema("{\"type\": \"long\", \"logicalType\": \"timestamp-millis\"}")
    public DateTime timestampMillis;

    @AvroSchema("{\"name\": \"TestEnum\", \"type\": \"enum\", \"symbols\": [\"abc\",\"cde\"] }")
    public TestEnum testEnum;

    @org.apache.avro.reflect.Nullable public AvroSubPojo row;
    @org.apache.avro.reflect.Nullable public List<AvroSubPojo> array;
    @org.apache.avro.reflect.Nullable public Map<String, AvroSubPojo> map;
    @AvroIgnore String extraField;

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AvroPojo)) {
        return false;
      }
      AvroPojo avroPojo = (AvroPojo) o;
      return boolNonNullable == avroPojo.boolNonNullable
          && Objects.equals(anInt, avroPojo.anInt)
          && Objects.equals(aShort, avroPojo.aShort)
          && Objects.equals(aLong, avroPojo.aLong)
          && Objects.equals(aFloat, avroPojo.aFloat)
          && Objects.equals(aDouble, avroPojo.aDouble)
          && Objects.equals(string, avroPojo.string)
          && Objects.equals(bytes, avroPojo.bytes)
          && Arrays.equals(fixed, avroPojo.fixed)
          && Objects.equals(decimalScale, avroPojo.decimalScale)
          && Objects.equals(date, avroPojo.date)
          && Objects.equals(timestampMillis, avroPojo.timestampMillis)
          && Objects.equals(testEnum, avroPojo.testEnum)
          && Objects.equals(row, avroPojo.row)
          && Objects.equals(array, avroPojo.array)
          && Objects.equals(map, avroPojo.map);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          boolNonNullable,
          anInt,
          aShort,
          aLong,
          aFloat,
          aDouble,
          string,
          bytes,
          Arrays.hashCode(fixed),
          decimalScale,
          date,
          timestampMillis,
          testEnum,
          row,
          array,
          map);
    }

    public AvroPojo(
        boolean boolNonNullable,
        int anInt,
        Integer aShort,
        long aLong,
        float aFloat,
        double aDouble,
        String string,
        ByteBuffer bytes,
        byte[] fixed,
        BigDecimal decimalScale,
        LocalDate date,
        DateTime timestampMillis,
        TestEnum testEnum,
        AvroSubPojo row,
        List<AvroSubPojo> array,
        Map<String, AvroSubPojo> map) {
      this.boolNonNullable = boolNonNullable;
      this.anInt = anInt;
      this.aShort = aShort;
      this.aLong = aLong;
      this.aFloat = aFloat;
      this.aDouble = aDouble;
      this.string = string;
      this.bytes = bytes;
      this.fixed = fixed;
      this.decimalScale = decimalScale;
      this.date = date;
      this.timestampMillis = timestampMillis;
      this.testEnum = testEnum;
      this.row = row;
      this.array = array;
      this.map = map;
      this.extraField = "";
    }

    public AvroPojo() {}

    @Override
    public String toString() {
      return "AvroPojo{"
          + "boolNonNullable="
          + boolNonNullable
          + ", anInt="
          + anInt
          + ", aShort="
          + aShort
          + ", aLong="
          + aLong
          + ", aFloat="
          + aFloat
          + ", aDouble="
          + aDouble
          + ", string='"
          + string
          + '\''
          + ", bytes="
          + bytes
          + ", fixed="
          + Arrays.toString(fixed)
          + ", decimalScale="
          + decimalScale
          + ", date="
          + date
          + ", timestampMillis="
          + timestampMillis
          + ", testEnum="
          + testEnum
          + ", row="
          + row
          + ", array="
          + array
          + ", map="
          + map
          + ", extraField='"
          + extraField
          + '\''
          + '}';
    }
  }

  private static final Schema SUBSCHEMA =
      Schema.builder()
          .addField("BOOL_NON_NULLABLE", FieldType.BOOLEAN)
          .addNullableField("int", FieldType.INT32)
          .build();
  private static final FieldType SUB_TYPE = FieldType.row(SUBSCHEMA).withNullable(true);

  private static final EnumerationType TEST_ENUM_TYPE = EnumerationType.create("abc", "cde");

  private static final Schema SCHEMA =
      Schema.builder()
          .addField("bool_non_nullable", FieldType.BOOLEAN)
          .addNullableField("int", FieldType.INT32)
          .addNullableField("short", FieldType.INT16)
          .addNullableField("long", FieldType.INT64)
          .addNullableField("float", FieldType.FLOAT)
          .addNullableField("double", FieldType.DOUBLE)
          .addNullableField("string", FieldType.STRING)
          .addNullableField("bytes", FieldType.BYTES)
          .addField("fixed", FieldType.logicalType(FixedBytes.of(4)))
          .addNullableField("decimalScale", FieldType.logicalType(FixedPrecisionNumeric.of(10, 2)))
          .addField("date", FieldType.DATETIME)
          .addField("timestampMillis", FieldType.DATETIME)
          .addField("TestEnum", FieldType.logicalType(TEST_ENUM_TYPE))
          .addNullableField("row", SUB_TYPE)
          .addNullableField("array", FieldType.array(SUB_TYPE))
          .addNullableField("map", FieldType.map(FieldType.STRING, SUB_TYPE))
          .build();
  private static final Schema NOINT16_SCHEMA =
      Schema.builder()
          .addFields(
              SCHEMA.getFields().stream()
                  .map(
                      f -> {
                        if (f.getName().equals("short")) {
                          return Schema.Field.nullable("short", FieldType.INT32);
                        } else {
                          return f;
                        }
                      })
                  .collect(Collectors.toList()))
          .build();

  private static final Schema POJO_SCHEMA =
      Schema.builder()
          .addField("bool_non_nullable", FieldType.BOOLEAN)
          .addNullableField("int", FieldType.INT32)
          .addNullableField("short", FieldType.INT16)
          .addNullableField("long", FieldType.INT64)
          .addNullableField("float", FieldType.FLOAT)
          .addNullableField("double", FieldType.DOUBLE)
          .addNullableField("string", FieldType.STRING)
          .addNullableField("bytes", FieldType.BYTES)
          .addField("fixed", FieldType.logicalType(FixedBytes.of(4)))
          .addNullableField("decimalScale", FieldType.logicalType(FixedPrecisionNumeric.of(10, 2)))
          .addField("date", FieldType.DATETIME)
          .addField("timestampMillis", FieldType.DATETIME)
          .addField("testEnum", FieldType.logicalType(TEST_ENUM_TYPE))
          .addNullableField("row", SUB_TYPE)
          .addNullableField("array", FieldType.array(SUB_TYPE.withNullable(false)))
          .addNullableField("map", FieldType.map(FieldType.STRING, SUB_TYPE.withNullable(false)))
          .build();
  private static final Schema NOINT16_POJO_SCHEMA =
      Schema.builder()
          .addFields(
              POJO_SCHEMA.getFields().stream()
                  .map(
                      f -> {
                        if (f.getName().equals("short")) {
                          return Schema.Field.nullable("short", FieldType.INT32);
                        } else {
                          return f;
                        }
                      })
                  .collect(Collectors.toList()))
          .build();

  private static final byte[] BYTE_ARRAY = new byte[] {1, 2, 3, 4};
  private static final DateTime DATE_TIME =
      new DateTime().withDate(1979, 3, 14).withTime(1, 2, 3, 4);
  private static final LocalDate DATE = new LocalDate(1979, 3, 14);
  private static final TestAvroNested AVRO_NESTED_SPECIFIC_RECORD = new TestAvroNested(true, 42);
  private static final TestAvro AVRO_SPECIFIC_RECORD =
      TestAvroFactory.newInstance(
          true,
          43,
          42,
          44L,
          (float) 44.1,
          (double) 44.2,
          "mystring",
          ByteBuffer.wrap(BYTE_ARRAY),
          new fixed4(BYTE_ARRAY),
          BigDecimal.valueOf(12345, 2),
          DATE,
          DATE_TIME,
          TestEnum.abc,
          AVRO_NESTED_SPECIFIC_RECORD,
          ImmutableList.of(AVRO_NESTED_SPECIFIC_RECORD, AVRO_NESTED_SPECIFIC_RECORD),
          ImmutableMap.of("k1", AVRO_NESTED_SPECIFIC_RECORD, "k2", AVRO_NESTED_SPECIFIC_RECORD));
  private static final GenericRecord AVRO_NESTED_GENERIC_RECORD =
      new GenericRecordBuilder(TestAvroNested.SCHEMA$)
          .set("BOOL_NON_NULLABLE", true)
          .set("int", 42)
          .build();
  private static final GenericRecord AVRO_GENERIC_RECORD =
      new GenericRecordBuilder(TestAvro.SCHEMA$)
          .set("bool_non_nullable", true)
          .set("int", 43)
          .set("short", 42) // TODO avro can't yield short
          .set("long", 44L)
          .set("float", (float) 44.1)
          .set("double", (double) 44.2)
          .set("string", new Utf8("mystring"))
          .set("bytes", ByteBuffer.wrap(BYTE_ARRAY))
          .set(
              "fixed",
              GenericData.get()
                  .createFixed(
                      null, BYTE_ARRAY, org.apache.avro.Schema.createFixed("fixed4", "", "", 4)))
          .set(
              "decimalScale",
              ByteBuffer.wrap(BigDecimal.valueOf(12345, 2).unscaledValue().toByteArray()))
          .set("date", (int) Days.daysBetween(new LocalDate(1970, 1, 1), DATE).getDays())
          .set("timestampMillis", DATE_TIME.getMillis())
          .set("TestEnum", TestEnum.abc)
          .set("row", AVRO_NESTED_GENERIC_RECORD)
          .set("array", ImmutableList.of(AVRO_NESTED_GENERIC_RECORD, AVRO_NESTED_GENERIC_RECORD))
          .set(
              "map",
              ImmutableMap.of(
                  new Utf8("k1"), AVRO_NESTED_GENERIC_RECORD,
                  new Utf8("k2"), AVRO_NESTED_GENERIC_RECORD))
          .build();

  private static final Row NESTED_ROW = Row.withSchema(SUBSCHEMA).addValues(true, 42).build();
  private static final Row ROW =
      Row.withSchema(SCHEMA)
          .addValues(
              true,
              43,
              (short) 42,
              44L,
              (float) 44.1,
              (double) 44.2,
              "mystring",
              ByteBuffer.wrap(BYTE_ARRAY),
              BYTE_ARRAY,
              BigDecimal.valueOf(12345, 2),
              DATE.toDateTimeAtStartOfDay(DateTimeZone.UTC),
              DATE_TIME,
              TEST_ENUM_TYPE.valueOf("abc"),
              NESTED_ROW,
              ImmutableList.of(NESTED_ROW, NESTED_ROW),
              ImmutableMap.of("k1", NESTED_ROW, "k2", NESTED_ROW))
          .build();

  private static final Row NOINT16_ROW =
      Row.withSchema(NOINT16_SCHEMA)
          .addValues(
              true,
              43,
              42,
              44L,
              (float) 44.1,
              (double) 44.2,
              "mystring",
              ByteBuffer.wrap(BYTE_ARRAY),
              BYTE_ARRAY,
              BigDecimal.valueOf(12345, 2),
              DATE.toDateTimeAtStartOfDay(DateTimeZone.UTC),
              DATE_TIME,
              TEST_ENUM_TYPE.valueOf("abc"),
              NESTED_ROW,
              ImmutableList.of(NESTED_ROW, NESTED_ROW),
              ImmutableMap.of("k1", NESTED_ROW, "k2", NESTED_ROW))
          .build();

  @Test
  public void testSpecificRecordSchema() {
    Schema beamSchemaProduced = new AvroRecordSchema().schemaFor(TypeDescriptor.of(TestAvro.class));
    assertNotEquals("Avro can't yield INT16", SCHEMA, beamSchemaProduced);
    assertEquals("Avro can't yield INT16", NOINT16_SCHEMA, beamSchemaProduced);
  }

  @Test
  public void testPojoSchema() {
    // TODO Short in AvroPojo may be only converted in INT32, but Pojo_SCHEMA may hold it as INT16
    Schema actual = new AvroRecordSchema().schemaFor(TypeDescriptor.of(AvroPojo.class));
    assertThat(actual, equivalentTo(NOINT16_POJO_SCHEMA));
    assertThat(actual, not(equivalentTo(POJO_SCHEMA)));
  }

  @Test
  public void testSpecificRecordToRow() {
    SerializableFunction<TestAvro, Row> toRow =
        new AvroRecordSchema().toRowFunction(TypeDescriptor.of(TestAvro.class));
    assertEquals(NOINT16_ROW, toRow.apply(AVRO_SPECIFIC_RECORD));
  }

  @Test
  public void testRowToSpecificRecord() {
    SerializableFunction<Row, TestAvro> fromRow =
        new AvroRecordSchema().fromRowFunction(TypeDescriptor.of(TestAvro.class));
    TestAvro actualAvro = fromRow.apply(ROW);
    SpecificData.get()
        .addLogicalTypeConversion(
            new Conversions
                .DecimalConversion()); // TODO it's just for comparison BigDecimal in Union for
    // nullability
    assertEquals(AVRO_SPECIFIC_RECORD, actualAvro);
  }

  @Test
  public void testGenericRecordToRow() {
    SerializableFunction<GenericRecord, Row> toRow =
        AvroUtils.getGenericRecordToRowFunction(SCHEMA);
    assertEquals(ROW, toRow.apply(AVRO_GENERIC_RECORD));
  }

  @Test
  public void testRowToGenericRecord() {
    SerializableFunction<Row, GenericRecord> fromRow =
        AvroUtils.getRowToGenericRecordFunction(TestAvro.SCHEMA$);
    // TODO avro can't yield short !!
    assertEquals(AVRO_GENERIC_RECORD, fromRow.apply(ROW));
  }

  private static final AvroSubPojo SUB_POJO = new AvroSubPojo(true, 42);
  private static final AvroPojo AVRO_POJO =
      new AvroPojo(
          true,
          43,
          42,
          44L,
          (float) 44.1,
          (double) 44.2,
          "mystring",
          ByteBuffer.wrap(BYTE_ARRAY),
          BYTE_ARRAY,
          BigDecimal.valueOf(12345, 2),
          DATE,
          DATE_TIME,
          TestEnum.abc,
          SUB_POJO,
          ImmutableList.of(SUB_POJO, SUB_POJO),
          ImmutableMap.of("k1", SUB_POJO, "k2", SUB_POJO));

  private static final Row ROW_FOR_POJO =
      Row.withSchema(NOINT16_POJO_SCHEMA)
          .addValues(
              true,
              43,
              42, // avro may only yield int
              44L,
              (float) 44.1,
              (double) 44.2,
              "mystring",
              ByteBuffer.wrap(BYTE_ARRAY),
              BYTE_ARRAY,
              BigDecimal.valueOf(12345, 2),
              DATE.toDateTimeAtStartOfDay(DateTimeZone.UTC),
              DATE_TIME,
              TEST_ENUM_TYPE.valueOf("abc"),
              NESTED_ROW,
              ImmutableList.of(NESTED_ROW, NESTED_ROW),
              ImmutableMap.of("k1", NESTED_ROW, "k2", NESTED_ROW))
          .build();

  private static final Row ROW_FOR_POJO_INT16 =
      Row.withSchema(POJO_SCHEMA)
          .addValues(
              true,
              43,
              (short) 42, // avro may only yield int
              44L,
              (float) 44.1,
              (double) 44.2,
              "mystring",
              ByteBuffer.wrap(BYTE_ARRAY),
              BYTE_ARRAY,
              BigDecimal.valueOf(12345, 2),
              DATE.toDateTimeAtStartOfDay(DateTimeZone.UTC),
              DATE_TIME,
              TEST_ENUM_TYPE.valueOf("abc"),
              NESTED_ROW,
              ImmutableList.of(NESTED_ROW, NESTED_ROW),
              ImmutableMap.of("k1", NESTED_ROW, "k2", NESTED_ROW))
          .build();

  @Test
  public void testPojoRecordToRow() {
    SerializableFunction<AvroPojo, Row> toRow =
        new AvroRecordSchema().toRowFunction(TypeDescriptor.of(AvroPojo.class));
    assertThat(toRow.apply(AVRO_POJO), equivalentTo(ROW_FOR_POJO));
  }

  @Test
  public void testRowToPojo() {
    SerializableFunction<Row, AvroPojo> fromRow =
        new AvroRecordSchema().fromRowFunction(TypeDescriptor.of(AvroPojo.class));
    assertEquals(AVRO_POJO, fromRow.apply(ROW_FOR_POJO_INT16));
  }

  @Test
  public void testPojoRecordToRowSerializable() {
    SerializableUtils.ensureSerializableRoundTrip(
        new AvroRecordSchema().toRowFunction(TypeDescriptor.of(AvroPojo.class)));
  }

  @Test
  public void testPojoRecordFromRowSerializable() {
    SerializableUtils.ensureSerializableRoundTrip(
        new AvroRecordSchema().fromRowFunction(TypeDescriptor.of(AvroPojo.class)));
  }

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  @Category(ValidatesRunner.class)
  public void testAvroPipelineGroupBy() {
    PCollection<Row> input = pipeline.apply(Create.of(ROW_FOR_POJO).withRowSchema(POJO_SCHEMA));

    PCollection<Row> output = input.apply(Group.byFieldNames("string"));
    Schema keySchema = Schema.builder().addStringField("string").build();
    Schema outputSchema =
        Schema.builder()
            .addRowField("key", keySchema)
            .addIterableField("value", FieldType.row(POJO_SCHEMA))
            .build();
    PAssert.that(output)
        .containsInAnyOrder(
            Row.withSchema(outputSchema)
                .addValue(Row.withSchema(keySchema).addValue("mystring").build())
                .addIterable(ImmutableList.of(ROW_FOR_POJO))
                .build());

    pipeline.run();
  }
}
