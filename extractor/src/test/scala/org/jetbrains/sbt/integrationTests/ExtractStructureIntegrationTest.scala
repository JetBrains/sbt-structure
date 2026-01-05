package org.jetbrains.sbt.integrationTests

import org.jetbrains.sbt.MaxXmlWidthInTests
import org.jetbrains.sbt.integrationTests.utils.*
import org.jetbrains.sbt.structure.*
import org.jetbrains.sbt.structure.XmlSerializer.*
import org.scalatest.StreamlinedXml
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers.*

import java.io.File
import scala.xml.*
import sbt.io.syntax.fileToRichFile

class ExtractStructureIntegrationTest extends AnyFreeSpecLike {

  //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // Set this value to `true` to copy structure to expected
  // NOTE: you need to first run the tests in a normal mode and wait for all tests to finish,
  //       then rerun the test with this flag set to "true"
  // ATTENTION: carefully review the changes in expected data manually
  //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  private val CopyActualStructureToExpected = false

  private val TestDataRoot = new File("extractor/src/test/data/").getCanonicalFile

  import org.jetbrains.sbt.integrationTests.utils.LatestSbtVersions.*
  import org.jetbrains.sbt.integrationTests.utils.SbtOptionsBuilder.*

  "extracted structure should equal to expected structure" - {
    "sbt 0.13" - {
      "bare" in { testProject_013("bare", options = ResolveSourcesAndSbtClassifiers) }
      "multiple" in { testProject_013("multiple", options = ResolveSourcesAndSbtClassifiers) }
      "simple" in { testProject_013("simple", options = ResolveSourcesAndSbtClassifiers) }
      "classifiers" in { testProject_013("classifiers", options = ResolveSourcesAndSbtClassifiers) }
      "optional" in { testProject_013("optional", options = ResolveSourcesAndSbtClassifiers) }
      "play" in { testProject_013("play", options = ResolveNone) }
      "ide-settings" in { testProject_013("ide-settings", options = ResolveSourcesAndSbtClassifiers) }
      "sbt-idea" in { testProject_013("sbt-idea", options = ResolveSourcesAndSbtClassifiers) }
      "custom-source-generator" in { testProject_013("custom-source-generator", options = ResolveSourcesAndSbtClassifiers) }
      "custom-test-config" in { testProject_013("custom-test-config", options = ResolveSourcesAndSbtClassifiers) }
      "source-generator-failure" in { testProject_013("source-generator-failure", options = ResolveSourcesAndSbtClassifiers) }

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
        "custom-test-config" in { testProject_013("prod_test_sources_separated/custom-test-config", options = ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
      }
    }

    "1.0" - {
      "simple" in { testProject("simple", SbtVersion_1_0, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_0, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.1" - {
      "simple" in { testProject("simple", SbtVersion_1_1, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_1, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.2" - {
      "custom-source-generator" in { testProject("custom-source-generator", SbtVersion_1_2, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
      "simple" in { testProject("simple", SbtVersion_1_2, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_2, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
      "source-generator-failure" in { testProject("source-generator-failure", SbtVersion_1_2, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources, errorsExpected = true) }
    }
    "1.3" - {
      "simple" in { testProject("simple", SbtVersion_1_3, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_3, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.4" - {
      "simple" in { testProject("simple", SbtVersion_1_4, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_4, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }

    "1.5" - {
      "simple" in { testProject("simple", SbtVersion_1_5, ResolveSourcesAndSbtClassifiers) }
      "scala3 simple" in { testProject("simple_scala3", SbtVersion_1_5, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_5, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.6" - {
      "simple" in { testProject("simple", SbtVersion_1_6, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_6, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.7" - {
      "simple" in { testProject("simple", SbtVersion_1_7, ResolveSourcesAndSbtClassifiers) }
      "compile-order" in { testProject("compile-order", SbtVersion_1_7, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_7, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }
    "1.8" - {
      "simple" in { testProject("simple", SbtVersion_1_8, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_8, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
    }

    "1.9" - {
      "dependency_resolve_none" in { testProject("dependency_resolve_none", SbtVersion_1_9, options = ResolveNone) }
      "dependency_resolve_none_with_explicit_classifiers" in { testProject("dependency_resolve_none_with_explicit_classifiers", SbtVersion_1_9, options = ResolveNone) }
      "dependency_resolve_sources" in { testProject("dependency_resolve_sources", SbtVersion_1_9, options = ResolveSources) }
      "dependency_resolve_javadocs" in { testProject("dependency_resolve_javadocs", SbtVersion_1_9, options = ResolveJavadocs) }
      "dependency_resolve_sbt_classifiers" in { testProject("dependency_resolve_sbt_classifiers", SbtVersion_1_9, options = ResolveSbtClassifiers) }
      "dependency_resolve_sources_and_javadocs_and_sbt_classifiers" in { testProject("dependency_resolve_sources_and_javadocs_and_sbt_classifiers", SbtVersion_1_9, options = ResolveSourcesAndJavaDocsAndSbtClassifiers) }
      "internal_config" in { testProject("internal_config", SbtVersion_1_9, options = ResolveSources) }
      "dependency_resolve_sbt_classifiers_prod_test_sources_separated" in { testProject("dependency_resolve_sbt_classifiers_prod_test_sources_separated", SbtVersion_1_9, options = ResolveSbtClassifiersAndSeparateProdTestSources) }
      "multipleProjectsDeps" in { testProject("multipleProjectsDeps", SbtVersion_1_9, options = ResolveNoneAndSeparateProdTestSources) }
    }
    "1.10" - {
      "custom-source-generator" in { testProject("custom-source-generator", SbtVersion_1_10, ResolveSourcesAndSbtClassifiers) }
      "simple" in { testProject("simple", SbtVersion_1_10, ResolveSourcesAndSbtClassifiers) }
      "prod_test_sources_separated" in { testProject("prod_test_sources_separated", SbtVersion_1_10, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
      "source-generator-failure" in { testProject("source-generator-failure", SbtVersion_1_10, ResolveSourcesAndSbtClassifiers, errorsExpected = true) }
    }
    "1.11" - {
      "buildinfo" in { testProject("buildinfo", SbtVersion_1_11, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
      "custom-source-generator" in { testProject("custom-source-generator", SbtVersion_1_11, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
      "source-generator-failure" in { testProject("source-generator-failure", SbtVersion_1_11, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources, errorsExpected = true) }
    }
    "1.12" - {
      "buildinfo" in { testProject("buildinfo", SbtVersion_1_12, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
      "custom-source-generator" in { testProject("custom-source-generator", SbtVersion_1_12, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources) }
      "source-generator-failure" in { testProject("source-generator-failure", SbtVersion_1_12, ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources, errorsExpected = true) }
    }

    // In sbt 2.0.0-RC7, a binary compatibility breaking change was made which removed the
    // scalaCompilerBridgeBinaryJar key which we rely on.
    // We have put a compatibility task in place which makes sure that project imports can still be done
    // using sbt pre 2.0.0-RC7.
    // https://github.com/sbt/sbt/commit/68b2b7d0251d9bf352739f904d64590e9e9e8396
    "2.0.0-RC6" - {

      // TODO: uncomment sbtClassifiers when https://github.com/sbt/sbt/pull/8024 is uploaded
      // (and update sbt version)
      val options = SbtOptionsBuilder().sources /*.sbtClassifiers*/.separateProdTestSources.result
      "simple" in { testProject("simple", SbtVersion_2_legacy, options) }
      "buildinfo" in { testProject("buildinfo", SbtVersion_2_legacy, options) }
      "custom-source-generator" in { testProject("custom-source-generator", SbtVersion_2_legacy, options) }
      "source-generator-failure" in { testProject("source-generator-failure", SbtVersion_2_legacy, options, errorsExpected = true) }
    }

    "2.0" - {
      // TODO: uncomment sbtClassifiers when https://github.com/sbt/sbt/pull/8024 is uploaded
      // (and update sbt version)
      val options = SbtOptionsBuilder().sources /*.sbtClassifiers*/.separateProdTestSources.result
      "simple" in { testProject("simple", SbtVersion_2, options) }
      "buildinfo" in { testProject("buildinfo", SbtVersion_2, options) }
      "custom-source-generator" in { testProject("custom-source-generator", SbtVersion_2, options) }
      "source-generator-failure" in { testProject("source-generator-failure", SbtVersion_2, options, errorsExpected = true) }
    }
  }


  private def testProject_013(
    project: String,
    options: String
  ): Unit = testProject(
    project,
    SbtVersion_0_13,
    options
  )

  /**
   * @param sbtVersionFull version of sbt which will be used to run the tests.<br>
   *                       It's later passed to the sbt launcher process via `-Dsbt.version` VM option
   */
  private def testProject(
    projectDirName: String,
    sbtVersionFull: Version,
    options: String,
    errorsExpected: Boolean = false
  ): Unit = {
    val runOptions = CurrentEnvironment.buildSbtRunCommonOptions(sbtVersionFull, errorsExpected)
    import runOptions.sbtVersionShort

    val projectDir =
      if (sbtVersionFull == SbtVersion_2_legacy)
        TestDataRoot / SbtVersion_2_legacy.presentation / projectDirName
      else
        TestDataRoot / sbtVersionShort.presentation / projectDirName

    println(s"Running test: $projectDirName, sbtVersion: $sbtVersionFull, path: $projectDir")

    withClue(s"Test data folder doesn't exist: $projectDir") {
      projectDir must exist
    }

    // See `build.sbt` to better understand the logic
    val pluginClassesDir: File = PluginArtifactsUtils.getPluginUnpublishedClassesDirectory(sbtVersionShort)

    // Support different versions of the expected structure file name:
    // - structure-0.13.18.xml
    // - structure-1.0.xml
    // - structure.xml
    val testDataFile: File = {
      val testDataFileFull = new File(projectDir, s"structure-$sbtVersionFull.xml")
      val testDataFileMid = new File(projectDir, s"structure-$sbtVersionShort.xml")
      val testDataFileShort = new File(projectDir, s"structure.xml")

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
      FileUtils.writeStringToFile(testDataFile, FileUtils.read(new File(projectDir, s"actual-structure-$sbtVersionFull-for-test-data.xml")))
      return
    }

    val pathVarsSubstitutor = new PathVariablesSubstitutor(projectDir, runOptions)

    val expectedXmlStrNormalized: String = {
      val raw = FileUtils.read(testDataFile)
      val normalized = raw.normalizeFilePathSeparatorsInXml
      val withVarsSubstituted = pathVarsSubstitutor.replaceVarsWithPaths(normalized)
      withVarsSubstituted
    }

    MaxXmlWidthInTests = Some(512)

    val loadResult = SbtStructureLoader.dumpSbtStructure(
      projectDir,
      options,
      pluginFile = pluginClassesDir,
      runOptions = runOptions,
    )
    val actualXmlStringNotSanitized = loadResult.structure.normalizeFilePathSeparatorsInXml

    val actualXmlSanitizedAndFormatted = {
      val sanitized = XmlUtils.readXmlStringSanitizedAndFormatted(actualXmlStringNotSanitized)
      RegexSubstitutor.replaceMachineSpecificObjectImports(sanitized)
    }
    val expectedXmlSanitizedAndFormatted = XmlUtils.readXmlStringSanitizedAndFormatted(expectedXmlStrNormalized)

    val actualXml = XML.loadString(actualXmlSanitizedAndFormatted)
    val expectedXml = XML.loadString(expectedXmlSanitizedAndFormatted)

    val actualData = actualXml.deserialize[StructureData].right.get
    val expectedData = expectedXml.deserialize[StructureData].right.get

    val actualDataPretty = PrettyPrintUtils.prettyPrintCaseClass(actualData)
    val expectedDataPretty = PrettyPrintUtils.prettyPrintCaseClass(expectedData)

    def dumpFiles(): Unit = {
      FileUtils.writeStringToFile(new File(projectDir, "class-structure-actual.txt"), actualDataPretty)
      FileUtils.writeStringToFile(new File(projectDir, "class-structure-expected.txt"), expectedDataPretty)

      val actualStrForTestData: String = {
        val pathsVars = pathVarsSubstitutor.replacePathsWithVars(actualXmlSanitizedAndFormatted)
        val regexes = RegexSubstitutor.replaceMachineSpecificObjectImports(pathsVars)
        regexes.normalizeFilePathSeparatorsInXml
      }

      FileUtils.writeStringToFile(new File(projectDir, s"actual-structure-$sbtVersionFull-original.xml"), actualXmlStringNotSanitized)
      FileUtils.writeStringToFile(new File(projectDir, s"actual-structure-$sbtVersionFull-for-test-data.xml"), actualStrForTestData)
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
}
