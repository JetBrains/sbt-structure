package org.jetbrains.sbt.structure

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
}
