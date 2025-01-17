package org.jetbrains.sbt

import org.jetbrains.sbt.structure._
import org.jetbrains.sbt.structure.XmlSerializer._
import org.scalatest.StreamlinedXml
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers._

import java.io.{File, PrintWriter}
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.xml._

class ImportSpec extends AnyFreeSpecLike {

  //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // Set this value to `true` to copy structure to expected
  // NOTE: you need to first run the tests in a normal mode and wait for all tests to finish
  //       then rerun the test with this flag set to "true"
  // ATTENTION: carefully review the changes in expected data manually
  //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  private val CopyActualStructureToExpected = false

  private val UserHome = new File(System.getProperty("user.home")).getCanonicalFile.ensuring(_.exists())
  private val SbtGlobalRoot = new File(UserHome, ".sbt-structure-global/").getCanonicalFile

  println(
    s"""Test SBT global root: $SbtGlobalRoot
       |See sbt-launcher logs in ${SbtGlobalRoot}boot/update.log""".stripMargin
  )

  private val TestDataRoot = new File("extractor/src/test/data/").getCanonicalFile

  import Options.Keys

  private val ResolveNone = ""
  private val ResolveNoneAndSeparateProdTestSources = Keys.SeparateProdAndTestSources
  private val ResolveSources = s"${Keys.ResolveSourceClassifiers}"
  private val ResolveJavadocs = s"${Keys.ResolveJavadocClassifiers}"
  private val ResolveSbtClassifiers = s"${Keys.ResolveSbtClassifiers}"
  private val ResolveSbtClassifiersAndSeparateProdTestSources = s"${Keys.ResolveSbtClassifiers} ${Keys.SeparateProdAndTestSources}"
  private val ResolveSourcesAndSbtClassifiers = s"${Keys.ResolveSourceClassifiers} ${Keys.ResolveSbtClassifiers}"
  private val ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources =
    s"${Keys.ResolveSourceClassifiers} ${Keys.ResolveSbtClassifiers} ${Keys.SeparateProdAndTestSources}"
  private val ResolveSourcesAndJavaDocsAndSbtClassifiers = s"${Keys.ResolveSourceClassifiers}, ${Keys.ResolveJavadocClassifiers}, ${Keys.ResolveSbtClassifiers}"

  "extracted structure should equal to expected structure" - {
    "sbt 0.13.18" - {
      "bare"               in { testProject_013("bare", options = ResolveSourcesAndSbtClassifiers) }
      "multiple"           in { testProject_013("multiple", options = ResolveSourcesAndSbtClassifiers) }
      "simple"             in { testProject_013("simple", options = ResolveSourcesAndSbtClassifiers) }
      "classifiers"        in { testProject_013("classifiers", options = ResolveSourcesAndSbtClassifiers) }
      "optional"           in { testProject_013("optional", options = ResolveSourcesAndSbtClassifiers) }
      "play"               in { testProject_013("play", options = ResolveNone) }
      "ide-settings"       in { testProject_013("ide-settings", options = ResolveSourcesAndSbtClassifiers) }
      "sbt-idea"           in { testProject_013("sbt-idea", options = ResolveSourcesAndSbtClassifiers) }
      "custom-test-config" in { testProject_013("custom-test-config", options = ResolveSourcesAndSbtClassifiers)}

      "dependency_resolve_none" in { testProject_013("dependency_resolve_none", options = ResolveNone) }
      "dependency_resolve_none_with_explicit_classifiers" in { testProject_013("dependency_resolve_none_with_explicit_classifiers", options = ResolveNone) }
      "dependency_resolve_sources" in { testProject_013("dependency_resolve_sources", options = ResolveSources) }
      "dependency_resolve_javadocs" in { testProject_013("dependency_resolve_javadocs", options = ResolveJavadocs) }

      "prod_test_sources_separated" - {
        "multiple" in { testProject_013("prod_test_sources_separated/multiple", options = ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
        "simple" in { testProject_013("prod_test_sources_separated/simple", options = ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
        "classifiers" in { testProject_013("prod_test_sources_separated/classifiers", options = ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
        "optional" in { testProject_013("prod_test_sources_separated/optional", options = ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
        "play" in { testProject_013("prod_test_sources_separated/play", options = ResolveNoneAndSeparateProdTestSources) }
        "custom-test-config" in { testProject_013("prod_test_sources_separated/custom-test-config", options = ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources)}
      }
    }

    "1.0" - {
      "simple" in { testProject("simple", "1.0.4", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.0.4", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.1" - {
      "simple" in { testProject("simple", "1.1.6", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.1.6", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.2" - {
      "simple" in { testProject("simple", "1.2.8", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.2.8", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.3" - {
      "simple" in { testProject("simple", "1.3.13", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.3.13", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.4" - {
      "simple" in { testProject("simple", "1.4.9", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.4.9", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.5" - {
      "simple" in { testProject("simple", "1.5.5", ResolveSourcesAndSbtClassifiers) }
      "scala3 simple" in { testProject("simple_scala3", "1.5.5", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.5.5", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.6" - {
      "simple" in { testProject("simple", "1.6.2", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.6.2", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.7" - {
      "simple" in { testProject("simple", "1.7.3", ResolveSourcesAndSbtClassifiers) }
      "compile-order" in { testProject("compile-order", "1.7.3", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.7.3", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.8" - {
      "simple" in { testProject("simple", "1.8.3", ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", "1.8.3", ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }

    "1.9" - {
      val SbtVersion_1_9 = "1.9.6"
      "dependency_resolve_none" in { testProject("dependency_resolve_none", SbtVersion_1_9, options = ResolveNone) }
      "dependency_resolve_none_with_explicit_classifiers" in { testProject("dependency_resolve_none_with_explicit_classifiers", SbtVersion_1_9, options = ResolveNone) }
      "dependency_resolve_sources" in { testProject("dependency_resolve_sources", SbtVersion_1_9, options = ResolveSources) }
      "dependency_resolve_javadocs" in { testProject("dependency_resolve_javadocs", SbtVersion_1_9, options = ResolveJavadocs) }
      "dependency_resolve_sbt_classifiers" in { testProject("dependency_resolve_sbt_classifiers", SbtVersion_1_9, options = ResolveSbtClassifiers) }
      "dependency_resolve_sources_and_javadocs_and_sbt_classifiers" in { testProject("dependency_resolve_sources_and_javadocs_and_sbt_classifiers", SbtVersion_1_9, options = ResolveSourcesAndJavaDocsAndSbtClassifiers) }

      "internal_config" in { testProject("internal_config", SbtVersion_1_9, options = ResolveSources) }

      "dependency_resolve_sbt_classifiers_prod_test_sources_separated" in { testProject("dependency_resolve_sbt_classifiers_prod_test_sources_separated", SbtVersion_1_9, options = ResolveSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.10" - {
      val SbtVersion_1_10 = "1.10.7"
      "simple" in { testProject("simple", SbtVersion_1_10, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_10, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
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
    options: String
  ): Unit = testProject(
    project,
    "0.13.18",
    options
  )

  private def testProject(
    project: String,
    sbtVersionFull: String,
    options: String
  ): Unit = {
    val sbtVersionShort = sbtVersionBinary(sbtVersionFull)
    val scalaVersion = sbtScalaVersion(sbtVersionShort)

    val sbtGlobalBase = new File(SbtGlobalRoot, sbtVersionShort).getCanonicalFile
    val sbtBootDir = new File(SbtGlobalRoot, "boot/").getCanonicalFile
    val sbtIvyHome = new File(SbtGlobalRoot, "ivy2/").getCanonicalFile
    val sbtCoursierHome = new File(SbtGlobalRoot, "coursier/").getCanonicalFile

    val base = new File(new File(TestDataRoot, sbtVersionShort), project)
    println(s"Running test: $project, sbtVersion: $sbtVersionFull, path: $base")

    withClue(s"Test data folder doesn't exist: $base") {
      base must exist
    }

    val pluginClassesDir: File = {
      //See `build.sbt` to better understand the logic
      val isBefore1_3 = sbtVersionShort.startsWith("1.0") || sbtVersionShort.startsWith("1.1") || sbtVersionShort.startsWith("1.2")
      val crossBuiltSbtVersion: String = if (sbtVersionShort.startsWith("0."))
        sbtVersionShort
      else if (isBefore1_3)
        "1.2"
      else
        "1.3"
      val file = new File(s"extractor/target/scala-$scalaVersion/sbt-$crossBuiltSbtVersion/classes/").getCanonicalFile
      file.ensuring(_.exists(), s"Plugin file does not exist: $file")
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

    if (CopyActualStructureToExpected) {
      dumpToFile(testDataFile, TestUtil.read(new File(base, s"actual-structure-$sbtVersionFull-for-test-data.xml")))
      return
    }

    val pathVarsSubstitutor = new PathVariablesSubstitutor(base, sbtIvyHome, sbtCoursierHome, sbtBootDir)

    val expectedXmlStrNormalized: String = {
      val raw = TestUtil.read(testDataFile)
      val normalized = raw.normalizeFilePathSeparatorsInXml
      val withVarsSubstituted = pathVarsSubstitutor.replaceVarsWithPaths(normalized)
      withVarsSubstituted
    }

    MaxXmlWidthInTests = Some(512)

    val actualXmlStringNotSanitized = Loader
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

    val actualXmlSanitizedAndFormatted = readXmlStringSanitizedAndFormatted(actualXmlStringNotSanitized)
    val expectedXmlSanitizedAndFormatted = readXmlStringSanitizedAndFormatted(expectedXmlStrNormalized)

    val actualXml = XML.loadString(actualXmlSanitizedAndFormatted)
    val expectedXml = XML.loadString(expectedXmlSanitizedAndFormatted)

    val actualData = actualXml.deserialize[StructureData].right.get
    val expectedData = expectedXml.deserialize[StructureData].right.get

    val actualDataPretty = prettyPrintCaseClass(actualData)
    val expectedDataPretty = prettyPrintCaseClass(expectedData)

    def dumpFiles(): Unit = {
      dumpToFile(new File(base, "class-structure-actual.txt"), actualDataPretty)
      dumpToFile(new File(base, "class-structure-expected.txt"), expectedDataPretty)

      val actualStrForTestData: String = pathVarsSubstitutor.replacePathsWithVars(actualXmlSanitizedAndFormatted).normalizeFilePathSeparatorsInXml
      dumpToFile(new File(base, s"actual-structure-$sbtVersionFull-original.xml"), actualXmlStringNotSanitized)
      dumpToFile(new File(base, s"actual-structure-$sbtVersionFull-for-test-data.xml"), actualStrForTestData)
    }

    try {
      val actualXmlStreamlined: Elem = StreamlinedXml.streamlined.normalized(actualXml)
      val expectedXmlStreamlined: Elem = StreamlinedXml.streamlined.normalized(expectedXml)

      if (actualXmlStreamlined != expectedXmlStreamlined) {
        actualXmlSanitizedAndFormatted mustEqual expectedXmlSanitizedAndFormatted // expecting to fail
      }

      // why do we even need to test both XML and classes?
      if (actualData != expectedData) {
        actualDataPretty mustEqual expectedDataPretty // expecting to fail
      }
    } finally {
      dumpFiles()
    }
  }

  /** Load and sanitize xml to exclude values that cause troubles when comparing in tests */
  private def readXmlStringSanitizedAndFormatted(xmlString: String): String = {
    import org.dom4j.Element
    import org.dom4j.io.{OutputFormat, SAXReader, XMLWriter}

    import java.io.{StringReader, StringWriter}

    val reader = new SAXReader()
    val document = reader.read(new StringReader(xmlString))

    def removeValueTags(node: Element): Unit = {
      val elements = node.elementIterator().asScala.toSeq
      elements.foreach { element =>
        if (element.getName == "setting") {
          element.elements("value").clear()
        }
        removeValueTags(element)
      }
    }

    removeValueTags(document.getRootElement)

    val format: OutputFormat = OutputFormat.createPrettyPrint()
    format.setIndentSize(2)
    //format.setExpandEmptyElements(true)
    format.setNewLineAfterDeclaration(false)
    format.setTrimText(true)
    format.setPadText(false) //workaround for https://github.com/dom4j/dom4j/issues/147


    val stringWriter = new StringWriter()
    val writer = new XMLWriter(stringWriter, format)
    writer.write(document)

    stringWriter.toString
  }

  private class PathVariablesSubstitutor(base: File, sbtIvyHome: File, sbtCoursierHome: File, sbtBootDir: File) {

    private lazy val varToPathSubstitutions: Seq[(String, String)] = Seq(
      "$URI_BASE"         -> base.getCanonicalFile.toURI.toString,
      "$BASE"             -> base.getCanonicalPath,
      "$IVY2"             -> sbtIvyHome.getCanonicalPath,
      "$COURSIER"         -> sbtCoursierHome.getCanonicalPath,
      "$SBT_BOOT"         -> sbtBootDir.getCanonicalPath,
      "file:$HOME"        -> s"file:${UserHome.getCanonicalPath.ensureStartsWithSlash}",
      "$HOME"             -> UserHome.getCanonicalPath
    ).map { case (_1, _2) =>
      (_1, _2.normalizeFilePathSeparatorsInXml)
    }

    private lazy val pathToVarSubstitutions = varToPathSubstitutions.map(_.swap)

    def replaceVarsWithPaths(text: String): String =
      varToPathSubstitutions.foldLeft(text) { case (acc, (from, to)) =>
        acc.replace(from, to)
      }

    def replacePathsWithVars(text: String): String =
      pathToVarSubstitutions.foldLeft(text) { case (acc, (from, to)) =>
        acc.replace(from, to)
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
    def normalizeFilePathSeparatorsInXml: String =
      value.replace("\\", "/")
  }
}
