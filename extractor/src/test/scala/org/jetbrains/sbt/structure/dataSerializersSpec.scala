package org.jetbrains.sbt.structure

import org.jetbrains.sbt.structure.Helpers._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.net.URI

/**
  * Created by jast on 2016-2-12.
  */
class dataSerializersSpec extends AnyFreeSpec {

  // this test would only fail on Windows because Mac/Unix allow pretty much any character in filenames
  "RicherFile.canonIfFile" - {
    "should not throw errors on invalid paths" - {
      val str = "-target:jvm-1.6"
      str.canonIfFile shouldEqual str
    }
  }

  // https://youtrack.jetbrains.com/issue/SCL-12292
  "resolverDataSerializer" - {
    "should serialize and deserialize local paths with non-ascii characters" - {
      val str = """<resolver name="preloaded" root="file:/C:/Users/Añdré Üser/.sbt/preloaded/"/>"""
      val original = ResolverData("preloaded", "file:/C:/Users/Añdré Üser/.sbt/preloaded/")

      val serialized = resolverDataSerializer.serialize(original)
      val deserialized = resolverDataSerializer.deserialize(serialized)

      val result = deserialized.right.get
      assert(result.name == original.name)

      // it won't deserialize to exactly the same format because there's a URI encode step in there
      // we just ensure that the serialization works at all
      new URI(result.root).getScheme shouldEqual "file"
    }
  }
}
