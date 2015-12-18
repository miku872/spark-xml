/*
 * Copyright 2014 Databricks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.databricks.spark.xml

import java.io.File
import java.nio.charset.UnsupportedCharsetException

import org.scalatest.{BeforeAndAfterAll, FunSuite}

import org.apache.spark.SparkContext
import org.apache.spark.sql.{Row, SQLContext}
import org.apache.spark.sql.types._

abstract class AbstractXmlSuite extends FunSuite with BeforeAndAfterAll {
  val tempEmptyDir = "target/test/empty/"

  val agesFile = "src/test/resources/ages.xml"
  val agesFileTag = "ROW"

  val agesAttributeFile = "src/test/resources/ages-attribute.xml"
  val agesAttributeFileTag = "ROW"

  val booksFile = "src/test/resources/books.xml"
  val booksFileTag = "book"

  val booksNestedObjectFile = "src/test/resources/books-nested-object.xml"
  val booksNestedObjectFileTag = "book"

  val booksNestedArrayFile = "src/test/resources/books-nested-array.xml"
  val booksNestedArrayFileTag = "book"

  val booksComplicatedFile = "src/test/resources/books-complicated.xml"
  val booksComplicatedFileTag = "book"
  val booksComplicatedFileRootTag = "books"

  val carsFile = "src/test/resources/cars.xml"
  val carsFileTag = "ROW"

  val carsUnbalancedFile = "src/test/resources/cars-unbalanced-elements.xml"
  val carsUnbalancedFileTag = "ROW"

  val nullNumbersFile = "src/test/resources/null-numbers.xml"
  val nullNumbersFileTag = "ROW"

  val emptyFile = "src/test/resources/empty.xml"
  val emptyFileTag = "ROW"

  val numCars = 3
  val numBooks = 12
  val numBooksComplicated = 3

  private var sqlContext: SQLContext = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    sqlContext = new SQLContext(new SparkContext("local[2]", "XmlSuite"))
  }

  override protected def afterAll(): Unit = {
    try {
      sqlContext.sparkContext.stop()
    } finally {
      super.afterAll()
    }
  }

  test("DSL test") {
    val results = sqlContext
      .xmlFile(carsFile, rowTag = carsFileTag)
      .select("year")
      .collect()

    assert(results.size === numCars)
  }

  test("DSL test bad charset name") {
    val exception = intercept[UnsupportedCharsetException] {
      val results = sqlContext
        .xmlFile(carsFile, rowTag = carsFileTag, charset = "1-9588-osi")
        .select("year")
        .collect()
    }
    assert(exception.getMessage.contains("1-9588-osi"))
  }

  test("DDL test") {
    sqlContext.sql(
      s"""
         |CREATE TEMPORARY TABLE carsTable
         |USING com.databricks.spark.xml
         |OPTIONS (path "$carsFile", rowTag "$carsFileTag")
      """.stripMargin.replaceAll("\n", " "))

    assert(sqlContext.sql("SELECT year FROM carsTable").collect().size === numCars)
  }

  test("DDL test with alias name") {
    assume(org.apache.spark.SPARK_VERSION.take(3) >= "1.5",
      "Datasource alias feature was added in Spark 1.5")

    sqlContext.sql(
      s"""
         |CREATE TEMPORARY TABLE carsTable
         |USING xml
         |OPTIONS (path "$carsFile", rootTag "$carsFileTag")
      """.stripMargin.replaceAll("\n", " "))

    assert(sqlContext.sql("SELECT year FROM carsTable").collect().size === numCars)
  }

//   TODO: We need to support mode
//  test("DSL test for DROPMALFORMED parsing mode") {
//    val results = new XmlReader()
//      .withParseMode(ParseModes.DROP_MALFORMED_MODE)
//      .withUseHeader(true)
//      .withParserLib(parserLib)
//      .xmlFile(sqlContext, carsFile)
//      .select("year")
//      .collect()
//
//    assert(results.size === numCars - 1)
//  }

  // TODO: We need to support mode
//  test("DSL test for FAILFAST parsing mode") {
//    val parser = new XmlReader()
//      .withParseMode(ParseModes.FAIL_FAST_MODE)
//      .withUseHeader(true)
//      .withParserLib(parserLib)
//
//    val exception = intercept[SparkException]{
//      parser.xmlFile(sqlContext, carsFile)
//        .select("year")
//        .collect()
//    }
//
//    assert(exception.getMessage.contains("Malformed line in FAILFAST mode: 2015,Chevy,Volt"))
//  }

  test("DSL test with empty file and known schema") {
    val results = new XmlReader()
      .withSchema(StructType(List(StructField("column", StringType, false))))
      .withRowTag(emptyFileTag)
      .xmlFile(sqlContext, emptyFile)
      .count()

    assert(results === 0)
  }

  test("DSL test with poorly formatted file and string schema") {
    val stringSchema = new StructType(
      Array(
        StructField("year", LongType, true),
        StructField("make", StringType, true),
        StructField("model", StringType, true),
        StructField("comment", StringType, true)
      )
    )
    val results = new XmlReader()
      .withSchema(stringSchema)
      .withRowTag(carsUnbalancedFileTag)
      .xmlFile(sqlContext, carsUnbalancedFile)
      .count()

    assert(results === 3)
  }

  test("DDL test with empty file") {
    sqlContext.sql(s"""
           |CREATE TEMPORARY TABLE carsTable
           |(year double, make string, model string, comments string, grp string)
           |USING com.databricks.spark.xml
           |OPTIONS (path "$emptyFile", rowTag "$emptyFileTag")
      """.stripMargin.replaceAll("\n", " "))

    assert(sqlContext.sql("SELECT count(*) FROM carsTable").collect().head(0) === 0)
  }

  test("SQL test insert overwrite") {
    TestUtils.deleteRecursively(new File(tempEmptyDir))
    new File(tempEmptyDir).mkdirs()
    sqlContext.sql(
      s"""
         |CREATE TEMPORARY TABLE booksTableIO
         |USING com.databricks.spark.xml
         |OPTIONS (path "$booksFile", rowTag "$booksFileTag")
      """.stripMargin.replaceAll("\n", " "))
    sqlContext.sql(
      s"""
         |CREATE TEMPORARY TABLE booksTableEmpty
         |(author string, description string, genre string,
         |id string, price double, publish_date string, title string)
         |USING com.databricks.spark.xml
         |OPTIONS (path "$tempEmptyDir")
      """.stripMargin.replaceAll("\n", " "))

    assert(sqlContext.sql("SELECT * FROM booksTableIO").collect().size === numBooks)
    assert(sqlContext.sql("SELECT * FROM booksTableEmpty").collect().isEmpty)

    sqlContext.sql(
      s"""
         |INSERT OVERWRITE TABLE booksTableEmpty
         |SELECT * FROM booksTableIO
      """.stripMargin.replaceAll("\n", " "))
    assert(sqlContext.sql("SELECT * FROM booksTableEmpty").collect().size == numBooks)
  }

  test("DSL save") {
    // Create temp directory
    TestUtils.deleteRecursively(new File(tempEmptyDir))
    new File(tempEmptyDir).mkdirs()
    val copyFilePath = tempEmptyDir + "books-copy.xml"

    val books = sqlContext.xmlFile(booksComplicatedFile, rowTag = booksComplicatedFileTag)
    books.saveAsXmlFile(copyFilePath,
      Map("rootTag" -> booksComplicatedFileRootTag, "rowTag" -> booksComplicatedFileTag))

    val booksCopy = sqlContext.xmlFile(copyFilePath + "/", rowTag = booksComplicatedFileTag)

    assert(booksCopy.count == books.count)
    assert(booksCopy.collect.map(_.toString).toSet === books.collect.map(_.toString).toSet)
  }

  test("DSL save dataframe not read from a XML file") {
    // Create temp directory
    TestUtils.deleteRecursively(new File(tempEmptyDir))
    new File(tempEmptyDir).mkdirs()
    val copyFilePath = tempEmptyDir + "data-copy.xml"

    val schema = StructType(
      List(StructField("a", ArrayType(ArrayType(StringType)), nullable = true)))
    val data = sqlContext.sparkContext.parallelize(
      List(List(List("aa", "bb"), List("aa", "bb")))).map(Row(_))
    val df = sqlContext.createDataFrame(data, schema)
    df.saveAsXmlFile(copyFilePath)

    // When [[ArrayType]] has [[ArrayType]] as elements, it is confusing what is the element
    // name for XML file. Now, it is "item". So, "item" field is additionally added
    // to wrap the element.
    val schemaCopy = StructType(
      List(StructField("a", ArrayType(
        StructType(List(StructField("item", ArrayType(StringType), nullable = true)))),
          nullable = true)))
    val dfCopy = sqlContext.xmlFile(copyFilePath + "/")

    assert(dfCopy.count == df.count)
    assert(dfCopy.schema === schemaCopy)
  }

  test("DSL test schema inferred correctly") {
    val results = sqlContext
      .xmlFile(booksFile, rowTag = booksFileTag)

    assert(results.schema == StructType(List(
      StructField("author", StringType, nullable = true),
      StructField("description", StringType, nullable = true),
      StructField("genre", StringType, nullable = true),
      StructField("id", StringType, nullable = true),
      StructField("price", DoubleType, nullable = true),
      StructField("publish_date", StringType, nullable = true),
      StructField("title", StringType, nullable = true))
    ))

    assert(results.collect().size === numBooks)
  }

  test("DSL test schema inferred correctly with sampling ratio") {
    val results = sqlContext
      .xmlFile(booksFile, rowTag = booksFileTag, samplingRatio = 0.5)

    assert(results.schema == StructType(List(
      StructField("author", StringType, nullable = true),
      StructField("description", StringType, nullable = true),
      StructField("genre", StringType, nullable = true),
      StructField("id", StringType, nullable = true),
      StructField("price", DoubleType, nullable = true),
      StructField("publish_date", StringType, nullable = true),
      StructField("title", StringType, nullable = true))
    ))

    assert(results.collect().size === numBooks)
  }

  test("DSL test schema (object) inferred correctly") {
    val results = sqlContext
      .xmlFile(booksNestedObjectFile, rowTag = booksNestedObjectFileTag)

    assert(results.schema == StructType(List(
      StructField("author", StringType, nullable = true),
      StructField("description", StringType, nullable = true),
      StructField("genre", StringType, nullable = true),
      StructField("id", StringType, nullable = true),
      StructField("price", DoubleType, nullable = true),
      StructField("publish_dates", StructType(
        List(StructField("publish_date", StringType))), nullable = true),
      StructField("title", StringType, nullable = true))
    ))

    assert(results.collect().size === numBooks)
  }

  test("DSL test schema (array) inferred correctly") {
    val results = sqlContext
      .xmlFile(booksNestedArrayFile, rowTag = booksNestedArrayFileTag)

    assert(results.schema == StructType(List(
      StructField("author", StringType, nullable = true),
      StructField("description", StringType, nullable = true),
      StructField("genre", StringType, nullable = true),
      StructField("id", StringType, nullable = true),
      StructField("price", DoubleType, nullable = true),
      StructField("publish_date", ArrayType(StringType), nullable = true),
      StructField("title", StringType, nullable = true))
    ))

    assert(results.collect().size === numBooks)
  }

  test("DSL test schema (complicated) inferred correctly") {
    val results = sqlContext
      .xmlFile(booksComplicatedFile, rowTag = booksComplicatedFileTag)

    assert(results.schema == StructType(List(
      StructField("author", StringType, nullable = true),
      StructField("genre", StructType(
        List(StructField("genreid", LongType),
          StructField("name", StringType))),
        nullable = true),
      StructField("id", StringType, nullable = true),
      StructField("price", DoubleType, nullable = true),
      StructField("publish_dates", StructType(
        List(StructField("publish_date",
            ArrayType(StructType(
                List(StructField("day", LongType, nullable = true),
                  StructField("month", LongType, nullable = true),
                  StructField("tag", StringType, nullable = true),
                  StructField("year", LongType, nullable = true))))))),
        nullable = true),
      StructField("title", StringType, nullable = true))
    ))

    assert(results.collect().size === numBooksComplicated)
  }

  test("DSL test with different data types") {
    val stringSchema = new StructType(
      Array(
        StructField("year", IntegerType, true),
        StructField("make", StringType, true),
        StructField("model", StringType, true),
        StructField("comment", StringType, true)
      )
    )
    val results = new XmlReader()
      .withSchema(stringSchema)
      .withRowTag(carsUnbalancedFileTag)
      .xmlFile(sqlContext, carsUnbalancedFile)
      .count()

    assert(results === 3)
  }

  test("DSL test inferred schema passed through") {
    val dataFrame = sqlContext
      .xmlFile(carsFile, rowTag = carsFileTag)

    val results = dataFrame
      .select("comment", "year")
      .where(dataFrame("year") === 2012)

    assert(results.first.getString(0) === "No comment")
    assert(results.first.getLong(1) === 2012)
  }

  test("DSL test nullable fields") {
    val results = new XmlReader()
      .withSchema(StructType(List(StructField("name", StringType, false),
                                  StructField("age", IntegerType, true))))
      .withRowTag(nullNumbersFileTag)
      .xmlFile(sqlContext, nullNumbersFile)
      .collect()

    assert(results.head.toSeq === Seq("alice", 35))
    assert(results(1).toSeq === Seq("bob", null))
    assert(results(2).toSeq === Seq("coc", 24))
  }
}

class XmlSuite extends AbstractXmlSuite {
}