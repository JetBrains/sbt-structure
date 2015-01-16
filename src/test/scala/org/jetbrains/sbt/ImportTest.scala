package org.jetbrains.sbt

import org.scalatest.FunSuite
import org.scalatest._
import java.io.File

/**
 * @author Pavel Fatin
 */
class ImportTest extends FunSuite {
  val DataDir = new File("src/test/data/" + TestCompat.sbtVersion)

  test("version assert") {
    assert(Set("0.13", "0.12").contains(TestCompat.sbtVersion))
  }

  test("bare projects") {
    doTest("bare", download = false)
  }

  test("android project") {
    doTest("android")
  }

  test("simple project") {
    doTest("simple")
  }

  test("managed dependency") {
    doTest("dependency")
  }

  test("multiple projects") {
    doTest("multiple")
  }

  test("multiple projects with classified deps") {
    doTest("classifiers")
  }

  test("project with optional dependency") {
    doTest("optional")
  }

  test("play project") {
    doTest("play", download = false, sbtVersion = "0.13.5")
  }

  private def doTest(project: String, download: Boolean = true, sbtVersion: String = TestCompat.sbtVersionFull) {
    val base = new File(DataDir, project)

    val actual = Loader.load(base, download, sbtVersion).mkString("\n")

    val expected = {
      val text = read(new File(base, "structure.xml")).mkString("\n")
      val androidHome = Option(System.getenv.get("ANDROID_HOME")) getOrElse ""
      text.replace("$BASE", FS.toPath(base)).replace("$ANDROID_HOME", androidHome)
    }

    if (actual != expected) {
      println("Actual output:\n" + actual)
      fail()
    }
  }
}

