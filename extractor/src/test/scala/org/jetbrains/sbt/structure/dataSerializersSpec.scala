package org.jetbrains.sbt.structure

import java.net.URI

import org.jetbrains.sbt.structure.Helpers._
import org.specs2.mutable._

/**
  * Created by jast on 2016-2-12.
  */
class dataSerializersSpec extends Specification {

  "RicherFile.canonIfFile" should {
    // this test would only fail on Windows because Mac/Unix allow pretty much any character in filenames
    "not throw errors on invalid paths" in {
      val str = "-target:jvm-1.6"
      str.canonIfFile must beEqualTo (str)
    }
  }

  // https://youtrack.jetbrains.com/issue/SCL-12292
  "resolverDataSerializer" should {
    "serialize and deserialize local paths with non-ascii characters" in {
      val str = """<resolver name="preloaded" root="file:/C:/Users/Añdré Üser/.sbt/preloaded/"/>"""
      val original = ResolverData("preloaded", "file:/C:/Users/Añdré Üser/.sbt/preloaded/")

      val serialized = resolverDataSerializer.serialize(original)
      val deserialized = resolverDataSerializer.deserialize(serialized)

      val result = deserialized.right.get
      assert(result.name == original.name)

      // it won't deserialize to exactly the same format because there's a URI encode step in there
      // we just ensure that the serialization works at all
      new URI(result.root).getScheme must_== "file"
    }
  }

}
