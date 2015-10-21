package org.jetbrains.sbt

import java.io.{File, PrintWriter}

import difflib._
import org.jetbrains.sbt.structure.XmlSerializer._
import org.jetbrains.sbt.structure._
import org.specs2.matcher._
import org.specs2.mutable._

import scala.collection.JavaConverters._
import scala.xml._

class ImportSpec extends Specification with XmlMatchers {

  "Actual structure" should {
    sequential // running 10 sbt instances at once is a bad idea unless you have >16G of ram

    equalExpectedOneIn("bare")
    equalExpectedOneIn("multiple")
    equalExpectedOneIn("simple")
    equalExpectedOneIn("dependency")
    equalExpectedOneIn("classifiers", sbt13only)
    equalExpectedOneIn("optional", sbt13only)
    equalExpectedOneIn("play", onlyFor("0.13.7", "0.13.9"), resolveClassifiers = false)
    equalExpectedOneIn("android-1.4", onlyFor("0.13.7", "0.13.9") and ifAndroidDefined)
    equalExpectedOneIn("android", sbt13only and ifAndroidDefined)
    equalExpectedOneIn("ide-settings", onlyFor("0.13.7", "0.13.9"))
    equalExpectedOneIn("sbt-idea", sbt13only)
    equalExpectedOneIn("custom-test-config", sbt13only)
    equalExpectedOneIn("eviction", sbt13only)
  }

  val SbtVersion = System.getProperty("structure.sbtversion.short")
  val SbtVersionFull = System.getProperty("structure.sbtversion.full")
  val ScalaVersion = System.getProperty("structure.scalaversion")

  val PluginFile = new File("extractor/target/scala-" + ScalaVersion + "/sbt-" + SbtVersionFull +"/classes/")
  val TestDataRoot = new File("extractor/src/test/data/" + SbtVersion)
  val AndroidHome = Option(System.getenv.get("ANDROID_HOME")).map(normalizePath)
  val UserHome = Option(System.getProperty("user.home")).map(normalizePath)

  private def equalExpectedOneIn(projectName: String, conditions: => MatchResult[Any] = always, resolveClassifiers: Boolean = true) =
    ("equal expected one in '" + projectName + "' project [" + SbtVersionFull + "]").in { _: String =>
      if (conditions.isSuccess)
        testProject(projectName, resolveClassifiers)
      else
        conditions
    }

  private def testProject(project: String, resolveClassifiers: Boolean) = {
    val base = new File(TestDataRoot, project)
    val testDataFile = new File(base, "structure-" + SbtVersionFull + ".xml")

    testDataFile must exist.setMessage("No test data for version " + SbtVersionFull + " found")

    val expectedStr = getExpectedStr(testDataFile, base)
    val actualStr = Loader.load(base, resolveClassifiers, SbtVersionFull, PluginFile, verbose = true).mkString("\n")
    val actualXml = XML.loadString(actualStr)
    val expectedXml = XML.loadString(expectedStr)
    val actual = actualXml.deserialize[StructureData].right.get
    val expected = expectedXml.deserialize[StructureData].right.get

    def formatErrorMessage(message: String, expected: String, actual: String): String =
      String.format("Project: %s %n%s %n%s", project, message, getDiff(expected, actual))

    lazy val onXmlFail = {
      dumpToFile(new File(base, "actual.xml"), actualStr)
      val errorMessage = "Xml files are not equal, compare 'actual.xml' and 'structure-" + SbtVersionFull + ".xml'"
      formatErrorMessage(errorMessage, expectedStr, actualStr)
    }

    lazy val onEqualsFail = {
      dumpToFile(new File(base, "actual.txt"), prettyPrintCaseClass(actual))
      dumpToFile(new File(base, "expected.txt"), prettyPrintCaseClass(expected))
      val errorMessage = "Objects are not equal, compare 'actual.txt' and 'expected.txt'"
      formatErrorMessage(errorMessage, prettyPrintCaseClass(expected), prettyPrintCaseClass(actual))
    }

    (actual == expected) must beTrue.updateMessage(_ => onEqualsFail)
    actualXml must beEqualToIgnoringSpace(expectedXml).updateMessage(_ => onXmlFail)
  }

  private def normalizePath(path: String): String =
    path.replace('\\', '/')

  private def getExpectedStr(testDataFile: File, base: File): String =
    read(testDataFile).mkString("\n")
      .replace("$BASE", normalizePath(base.getCanonicalPath))
      .replace("$ANDROID_HOME", AndroidHome.getOrElse(""))
      .replace("~/", UserHome.getOrElse("") + "/")

  private def getDiff(expected: String, actual: String): String = {
    import scala.collection.JavaConversions._

    val result = new StringBuilder
    def appendToResult(str: Any): Unit =
      result.append(str + System.lineSeparator)

    val diff = DiffUtils.diff(expected.lines.toList, actual.lines.toList)
    diff.getDeltas foreach { delta =>
      appendToResult("Expected:")
      delta.getOriginal.getLines.asScala.foreach(appendToResult)
      appendToResult("Actual:")
      delta.getRevised.getLines.asScala.foreach(appendToResult)
      appendToResult("")
    }

    appendToResult("")
    result.toString
  }

  private def dumpToFile(file: File, contents: String): Unit = {
    val writer = new PrintWriter(file)
    writer.write(contents)
    writer.close()
  }

  private def prettyPrintCaseClass(toPrint: Product): String = {
    val indentStep = "  "
    def print0(what: Any, indent: String): String = what match {
      case p : Product =>
        if (p.productArity == 0) {
          indent + p.productPrefix
        } else {
          indent + p.productPrefix + ":\n" +
            p.productIterator.map {
              case s : Seq[_] => s.map(x => print0(x, indent + indentStep)).mkString("\n")
              case pp : Product => print0(pp, indent + indentStep)
              case other => indent + indentStep + other.toString
            }.mkString("\n")
        }
      case other => indent + other.toString
    }

    print0(toPrint, indentStep)
  }

  private def sbt13only =
    SbtVersion must beEqualTo("0.13").orSkip(_ => "This test is for SBT 0.13.x only")

  private def onlyFor(versions: String*) =
    versions must contain[String](SbtVersionFull).orSkip(_ => "This test is for SBT " + versions.mkString(", ") + " only")

  private def ifAndroidDefined =
    AndroidHome must beSome.orSkip(_ => "ANDROID_HOME is not defined")

  private def always =
    true must beTrue
}
