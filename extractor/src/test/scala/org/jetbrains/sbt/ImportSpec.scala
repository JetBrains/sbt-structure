package org.jetbrains.sbt

import org.jetbrains.sbt.structure.XmlSerializer._
import org.jetbrains.sbt.structure._
import org.scalatest.StreamlinedXml
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers._

import java.io.{File, PrintWriter}
import scala.util.control.NonFatal
import scala.xml._
import scala.xml.transform.{RewriteRule, RuleTransformer}

class ImportSpec extends AnyFreeSpecLike {

  private val sbtGlobalRoot = new File(
    System.getProperty("user.home"),
    ".sbt-structure-global/"
  ).getCanonicalFile

  println(
    s"""Test SBT global root: $sbtGlobalRoot
       |See sbt-launcher logs in ${sbtGlobalRoot}boot/update.log""".stripMargin
  )

  private val TestDataRoot = new File("extractor/src/test/data/").getCanonicalFile
  private val AndroidHome =
    Option(System.getenv.get("ANDROID_HOME")).map(new File(_).getCanonicalFile)
  // assuming user.home is always defined
  private val UserHome = new File(System.getProperty("user.home")).getCanonicalFile

  "extracted structure should equal to expected structure" - {
    "sbt 0.13.18" - {
      "bare"               in { testProject_013("bare", options = "resolveClassifiers resolveSbtClassifiers") }
      "dependency"         in { testProject_013("dependency") }
      "multiple"           in { testProject_013("multiple") }
      "simple"             in { testProject_013("simple", options = "resolveClassifiers resolveSbtClassifiers") }
      "classifiers"        in { testProject_013("classifiers") }
      "optional"           in { testProject_013("optional") }
      "play"               in { testProject_013("play", options = "") }
      "ide-settings"       in { testProject_013("ide-settings") }
      "sbt-idea"           in { testProject_013("sbt-idea") }
      "custom-test-config" in { testProject_013("custom-test-config")}
    }

    "sbt 1.x" - {
      "simple 1.0" in { testProject("simple", "1.0.4") }
      "simple 1.1" in { testProject("simple", "1.1.6") }
      "simple 1.2" in { testProject("simple", "1.2.8") }
      "simple 1.3" in { testProject("simple", "1.3.13") }
      "simple 1.4" in { testProject("simple", "1.4.9") }
      "simple 1.5" in { testProject("simple", "1.5.5") }

      "simple_scala3 1.5" in { testProject("simple_scala3", "1.5.5") }

      "simple 1.6" in { testProject("simple", "1.6.2") }
      "simple 1.7" in { testProject("simple", "1.7.2") }
    }
  }


  private def sbtVersionBinary(sbtVersionFull: String) =
    sbtVersionFull.split('.') match {
      case Array(a, b, _) => s"$a.$b" // e.g. 0.13, 1.0, 1.5
    }

  private def sbtScalaVersion(sbtVersion: String) =
    sbtVersion.split('.') match {
      case Array("0", "13") => "2.10"
      case Array("1", _)    => "2.12"
      case _                => throw new IllegalArgumentException(s"sbt version not supported by this test: $sbtVersion")
    }

  private def testProject_013(
    project: String,
    options: String = "resolveClassifiers resolveSbtClassifiers resolveJavadocs"
  ): Unit = testProject(
    project,
    "0.13.18",
    options
  )

  private def testProject(
    project: String,
    sbtVersionFull: String,
    options: String = "resolveClassifiers resolveSbtClassifiers resolveJavadocs"
  ): Unit = {
    val sbtVersionShort = sbtVersionBinary(sbtVersionFull)
    val scalaVersion = sbtScalaVersion(sbtVersionShort)

    val sbtGlobalBase = new File(sbtGlobalRoot, sbtVersionShort).getCanonicalFile
    val sbtBootDir = new File(sbtGlobalRoot, "boot/").getCanonicalFile
    val sbtIvyHome = new File(sbtGlobalRoot, "ivy2/").getCanonicalFile
    val sbtCoursierHome = new File(sbtGlobalRoot, "coursier/").getCanonicalFile

    val base = new File(new File(TestDataRoot, sbtVersionShort), project)
    println(s"Running test: $project, sbtVersion: $sbtVersionFull, path: $base")

    withClue(s"Test data folder doesn't exist: $base") {
      base must exist
    }

    val pluginClassesDir: File = {
      val crossBuiltSbtVersion = {
        if (sbtVersionShort.startsWith("0.")) sbtVersionShort
        else "1.0" // we do not cross-publish to different 1.x plugin versions (not yet)
      }
      new File(s"extractor/target/scala-$scalaVersion/sbt-$crossBuiltSbtVersion/classes/").getCanonicalFile
    }

    // support different versions of expected structure file name
    // structure-0.13.18.xml
    // structure-1.0.xml
    // structure.xml
    val testDataFile: File = {
      val testDataFileFull = new File(base, s"structure-$sbtVersionFull.xml")
      val testDataFileMid = new File(base, s"structure-$sbtVersionShort.xml")
      val testDataFileShort = new File(base, s"structure.xml")

      val f =
        if (testDataFileFull.exists()) testDataFileFull
        else if (testDataFileMid.exists()) testDataFileMid
        else testDataFileShort

      withClue(s"No test data for version $sbtVersionFull found at ${testDataFileFull.getPath} or ${testDataFileMid.getPath} or ${testDataFileShort.getPath}") {
        f must exist
      }

      f
    }

    //uncomment to update test data from actual data fast (carefully review it manually)
//    dumpToFile(testDataFile, TestUtil.read(new File(base, s"actual-structure-$sbtVersionFull-for-test-data-1.xml")))
//    return

    val pathVarsSubstitutor = new PathVariablesSubstitutor(base, sbtIvyHome, sbtCoursierHome, sbtBootDir)

    val expectedXmlStrNormalized: String = {
      val raw = TestUtil.read(testDataFile)
      val normalized = raw.normalizeFilePathSeparatorsInXml
      val withVarsSubstituted = pathVarsSubstitutor.substitutePaths(normalized)
      withVarsSubstituted
    }

    val actualXmlStrOriginal = Loader
      .load(
        base,
        options,
        sbtVersionFull,
        pluginFile = pluginClassesDir,
        sbtGlobalBase = sbtGlobalBase,
        sbtBootDir = sbtBootDir,
        sbtIvyHome = sbtIvyHome,
        sbtCoursierHome = sbtCoursierHome
      )
      .normalizeFilePathSeparatorsInXml

    val actualXml = loadSanitizedXml(actualXmlStrOriginal)
    val expectedXml = loadSanitizedXml(expectedXmlStrNormalized)

    val actual = actualXml.deserialize[StructureData].right.get
    val expected = expectedXml.deserialize[StructureData].right.get

    // why do we even need to test both XML and classes?
    val actualPretty = prettyPrintCaseClass(actual)
    val expectedPretty = prettyPrintCaseClass(expected)

    val actualXmlStreamlined: Elem = StreamlinedXml.streamlined.normalized(actualXml)
    val expectedXmlStreamlined: Elem = StreamlinedXml.streamlined.normalized(expectedXml)

    val formatter = new PrettyPrinter(512, 4)
    val actualXmlFormatted: String = formatter.format(actualXmlStreamlined)
    val expectedXmlFormatted: String = formatter.format(expectedXmlStreamlined)

    def dumpFiles(): Unit = {
      dumpToFile(new File(base, "class-structure-actual.txt"), actualPretty)
      dumpToFile(new File(base, "class-structure-expected.txt"), expectedPretty)

      val actualStrForTestData1: String = pathVarsSubstitutor.substituteVars(actualXmlStrOriginal).normalizeFilePathSeparatorsInXml
      val actualStrForTestData2: String = pathVarsSubstitutor.substituteVars(actualXmlFormatted).normalizeFilePathSeparatorsInXml
      dumpToFile(new File(base, s"actual-structure-$sbtVersionFull-original.xml"), actualXmlStrOriginal)
      dumpToFile(new File(base, s"actual-structure-$sbtVersionFull-for-test-data-1.xml"), actualStrForTestData1)
      dumpToFile(new File(base, s"actual-structure-$sbtVersionFull-for-test-data-2.xml"), actualStrForTestData2)
    }

    try {
      if (actualXmlStreamlined != expectedXmlStreamlined) {
        actualXmlFormatted mustEqual expectedXmlFormatted // expecting to fail
      }

      if (actual != expected) {
        actualPretty mustEqual expectedPretty // expecting to fail
      }
    }
    catch {
      case NonFatal(ex) =>
        dumpFiles()
        throw ex
    }
  }

  private val xmlSanitizer: RuleTransformer = {
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
  private def loadSanitizedXml(xmlString: String): Elem = {
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

  private class PathVariablesSubstitutor(base: File, sbtIvyHome: File, sbtCoursierHome: File, sbtBootDir: File) {

    private lazy val pathSubstitutions: Seq[(String, Option[String])] = Seq(
      "$URI_BASE"         -> Some(base.getCanonicalFile.toURI.toString),
      "$BASE"             -> Some(base.getCanonicalPath),
      "$URI_ANDROID_HOME" -> AndroidHome.map(p => canon(p.toURI.toString)),
      "$ANDROID_HOME"     -> AndroidHome.map(p => canon(p.toString)),
      "$IVY2"             -> Some(sbtIvyHome.getCanonicalPath),
      "$COURSIER"         -> Some(sbtCoursierHome.getCanonicalPath),
      "$SBT_BOOT"         -> Some(sbtBootDir.getCanonicalPath),
      "file:$HOME"        -> Some("file:" + UserHome.getCanonicalPath.ensureStartsWithSlash),
      "$HOME"             -> Some(UserHome.getCanonicalPath)
    ).map { case (_1, _2) =>
      (_1, _2.map(_.normalizeFilePathSeparatorsInXml))
    }

    def substitutePaths(text: String): String =
      pathSubstitutions.foldLeft(text) { case (acc, (from, to)) =>
        acc.replace(from, to.getOrElse(""))
      }

    def substituteVars(text: String): String =
      pathSubstitutions.map(_.swap).foldLeft(text) {
        case (acc, (from, to)) =>
          from.fold(acc)(acc.replace(_, to))
      }
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
        val prefix = indent + p.productPrefix
        if (p.productArity == 0)
          prefix
        else {
          val values = p.productIterator
            .map {
              case s: Seq[_]   => s.map(x => print0(x, indent + indentStep)).mkString("\n")
              case pp: Product => print0(pp, indent + indentStep)
              case other       => indent + indentStep + other.toString
            }
            .mkString("\n")
          s"""$prefix:
             |$values""".stripMargin.replace("\r", "")
        }
      case other =>
        indent + other.toString
    }

    print0(toPrint, indentStep)
  }

  implicit class StringOps(private val value: String) {
    def ensureStartsWithSlash: String =
      if (value.startsWith("/")) value else "/" + value

    /**
      * Normalize path separator to easier test on Windows
      *
      * ATTENTION: this affects all backslashes, not only in file paths
      */
    def normalizeFilePathSeparatorsInXml: String = {
      val a = value.replace("\\", "/")
      val b = a.replaceAll("file:.*/preloaded", "file:/dummy/preloaded")
      b
    }
  }
}
