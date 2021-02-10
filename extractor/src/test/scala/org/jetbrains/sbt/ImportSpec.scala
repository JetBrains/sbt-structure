package org.jetbrains.sbt

import java.io.{File, PrintWriter}
import difflib._
import org.jetbrains.sbt.structure.XmlSerializer._
import org.jetbrains.sbt.structure._
import org.specs2.matcher._
import org.specs2.mutable._
import org.specs2.specification.core.{Fragment, Fragments}

import scala.collection.JavaConverters._
import scala.xml._
import scala.xml.transform.{RewriteRule, RuleTransformer}

class ImportSpec extends Specification with XmlMatchers with FileMatchers {

  val defaultTestSbtVersions = List("0.13.9", "0.13.13")

  sequential // running 10 sbt instances at once is a bad idea unless you have >16G of ram
  // TODO make it possible to run each of the tests separately
  "Actual structure" should {

    equalExpectedOneIn("bare", options = "resolveClassifiers resolveSbtClassifiers")
    equalExpectedOneIn("dependency")

    equalExpectedOneIn("multiple")
    equalExpectedOneIn("simple", options = "resolveClassifiers resolveSbtClassifiers")
    equalExpectedOneIn("classifiers")
    equalExpectedOneIn("optional")
    equalExpectedOneIn("play", options = "")
    equalExpectedOneIn("ide-settings")
    equalExpectedOneIn("sbt-idea")
    equalExpectedOneIn("custom-test-config", List("0.13.13"))
  }

  private val sbtGlobalRoot = new File(
    System.getProperty("user.home"),
    ".sbt-structure-global/"
  ).getCanonicalFile

  private val TestDataRoot = new File("extractor/src/test/data/").getCanonicalFile
  private val AndroidHome =
    Option(System.getenv.get("ANDROID_HOME")).map(new File(_).getCanonicalFile)
  // assuming user.home is always defined
  private val UserHome = new File(System.getProperty("user.home")).getCanonicalFile

  private def equalExpectedOneIn(
    projectName: String,
    sbtVersions: List[String] = defaultTestSbtVersions,
    options: String = "resolveClassifiers resolveSbtClassifiers resolveJavadocs"
  ): Fragments = Fragment.foreach(sbtVersions) { sbtVersionFull =>
        ("equal expected structure in '" + projectName + "' project [" + sbtVersionFull + "]")
          .in { _: String =>
            testProject(projectName, options, sbtVersionFull)
          }
      }

  private def sbtVersionBinary(sbtVersionFull: String) =
    sbtVersionFull.split('.') match {
      case Array("0", "13", _) => "0.13"
      case Array("1", _, _) => "1.0"
      case _ => throw new IllegalArgumentException("sbt version not supported by this test")
    }

  private def sbtScalaVersion(sbtVersion: String) =
    sbtVersion.split('.') match {
      case Array("0", "13") => "2.10"
      case Array("1", "0") => "2.12"
      case _ => throw new IllegalArgumentException("sbt version not supported by this test")
    }

  private def testProject(project: String,
                          options: String,
                          sbtVersionFull: String
                         ): MatchResult[Elem] = {

    val sbtVersion = sbtVersionBinary(sbtVersionFull)
    val scalaVersion = sbtScalaVersion(sbtVersion)
    val sbtGlobalBase =
      new File(sbtGlobalRoot, sbtVersion).getCanonicalFile
    val sbtBootDir = new File(sbtGlobalRoot, "boot/").getCanonicalFile
    val sbtIvyHome = new File(sbtGlobalRoot, "ivy2/").getCanonicalFile
    val base = new File(new File(TestDataRoot, sbtVersion), project)

    val PluginFile = new File(
      "extractor/target/scala-" + scalaVersion + "/sbt-" + sbtVersion + "/classes/"
    ).getCanonicalFile

    def structureFileName(suffix: String) =
      "structure-" + sbtVersionFull + suffix + ".xml"
    val testDataFile = new File(base, structureFileName(""))

    testDataFile must exist.setMessage(
      "No test data for version " + sbtVersionFull + " found at " + testDataFile.getPath
    )

    val expectedStr = getExpectedStr(testDataFile, base, sbtIvyHome, sbtBootDir)
      .replaceAll("file:/.*/preloaded", "file:/dummy/preloaded")
    val actualStr = Loader
      .load(
        base,
        options,
        sbtVersionFull,
        pluginFile = PluginFile,
        sbtGlobalBase = sbtGlobalBase,
        sbtBootDir = sbtBootDir,
        sbtIvyHome = sbtIvyHome
      )
      .replaceAll("file:/.*/preloaded", "file:/dummy/preloaded")

    val actualXml = loadSanitizedXml(actualStr)
    val expectedXml = loadSanitizedXml(expectedStr)

    val actual = actualXml.deserialize[StructureData].right.get
    val expected = expectedXml.deserialize[StructureData].right.get

    def formatErrorMessage(message: String,
                           expected: String,
                           actual: String): String =
      String.format(
        "Project: %s %n%s %n%s",
        project,
        message,
        getDiff(expected, actual)
      )

    def onFail(): Unit = {
      dumpToFile(new File(base, structureFileName("-actual")), actualStr)
    }

    def onXmlFail: String = {
      onFail()
      val errorMessage = "Xml files are not equal, compare 'actual.xml' and 'structure-" + sbtVersionFull + ".xml'"
      formatErrorMessage(errorMessage, expectedStr, actualStr)
    }

    def onEqualsFail: String = {
      onFail()
      val actualPretty = prettyPrintCaseClass(actual)
      val expectedPretty = prettyPrintCaseClass(expected)
      dumpToFile(new File(base, "actual.txt"), actualPretty)
      dumpToFile(new File(base, "expected.txt"), expectedPretty)
      val errorMessage =
        "Objects are not equal, compare 'actual.txt' and 'expected.txt'"

      formatErrorMessage(errorMessage, expectedPretty, actualPretty)
    }

    (actual == expected) must beTrue.updateMessage(_ => onEqualsFail)
    actualXml must beEqualToIgnoringSpace(expectedXml).updateMessage(
      _ => onXmlFail
    )
  }

  private val xmlSanitizer = {
    val filterSettingValues = new RewriteRule {
      override def transform(n: Node): Seq[Node] = n match {
        case e: Elem if e.label == "setting" =>
          // setting values can change depending on where they are run, don't include in comparison
          e.copy(child = e.child.filterNot(_.label == "value"))
        case other => other
      }
    }
    new RuleTransformer(filterSettingValues)
  }

  /** Load and sanitize xml to exclude values that cause troubles when comparing in tests. */
  private def loadSanitizedXml(xmlString: String) = {
    try {
      val xml = XML.loadString(xmlString)
      val transformed = xmlSanitizer.transform(xml)
      xml.copy(child = NodeSeq.seqToNodeSeq(transformed).head.child)
    } catch {
      case x: Throwable =>
        throw new RuntimeException("failed to load and sanitize xml string", x)
    }
  }

  private def canon(path: String): String =
    path.stripSuffix("/").stripSuffix("\\")

  private def getExpectedStr(testDataFile: File, base: File, sbtIvyHome: File, sbtBootDir: File): String = {
    val raw = TestUtil
      .read(testDataFile)
      .replace("$URI_BASE", base.getCanonicalFile.toURI.toString)
      .replace("$BASE", base.getCanonicalPath)
      .replace(
        "$URI_ANDROID_HOME",
        AndroidHome.map(p => canon(p.toURI.toString)).getOrElse("")
      )
      .replace(
        "$ANDROID_HOME",
        AndroidHome.map(p => canon(p.toString)).getOrElse("")
      )
      .replace("$IVY2", sbtIvyHome.getCanonicalPath)
      .replace("$SBT_BOOT", sbtBootDir.getCanonicalPath)
      .replace("$HOME", UserHome.getCanonicalPath)
    // re-serialize and deserialize again to normalize all system-dependent paths
    try {
      XML
        .loadString(raw)
        .deserialize[StructureData]
        .right
        .get
        .serialize
        .mkString
    } catch {
      case x: Exception =>
        throw new RuntimeException(
          "unable to read test data from " + testDataFile.getAbsolutePath,
          x
        )
    }
  }

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
      case p: Product =>
        if (p.productArity == 0) {
          indent + p.productPrefix
        } else {
          indent + p.productPrefix + ":\n" +
            p.productIterator
              .map {
                case s: Seq[_] =>
                  s.map(x => print0(x, indent + indentStep)).mkString("\n")
                case pp: Product => print0(pp, indent + indentStep)
                case other       => indent + indentStep + other.toString
              }
              .mkString("\n")
        }
      case other => indent + other.toString
    }

    print0(toPrint, indentStep)
  }

}
