package org.jetbrains.sbt.structure

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.File
import scala.xml.PrettyPrinter

class DataSerializersTest extends AnyFunSuiteLike {
  test("scalaDataSerializer") {
    val data = ScalaData(
      "myOrg",
      "1.2.3",
      Seq(new File("a/b/c").getAbsoluteFile),
      Seq(new File("a/b/c").getAbsoluteFile),
      Seq(new File("a/b/c").getAbsoluteFile),
      Some(new File("a/b/c").getAbsoluteFile),
      Seq("opt1", "opt2")
    )

    val elem = scalaDataSerializer.serialize(data)
    val dataDeserialized = scalaDataSerializer.deserialize(elem)
    dataDeserialized shouldBe Right(data)
  }
}
