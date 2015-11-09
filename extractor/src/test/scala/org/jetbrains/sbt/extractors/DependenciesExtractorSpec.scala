package org.jetbrains.sbt
package extractors

import org.specs2.mutable._
import structure._
import Utilities._
import sbt._

class DependenciesExtractorSpec extends Specification {

  val stubProject1 = ProjectRef(file("/tmp/test-project"), "project-1")
  val stubProject2 = ProjectRef(file("/tmp/test-project"), "project-2")
  val stubBuildDependencies = Some(BuildDependencies(Map(
    stubProject1 -> Seq(ResolvedClasspathDependency(stubProject2, Some("compile")))
  ), Map.empty))

  val emptyClasspath: sbt.Configuration => Keys.Classpath = _ => Nil

  "DependenciesExtractor" should {
    "always extract build dependencies" in {
      val actual = new DependenciesExtractor(
        stubProject1, stubBuildDependencies, emptyClasspath, emptyClasspath, Nil, Nil
      ).extract

      val expected = DependencyData(
        Seq(
          ProjectDependencyData(stubProject1.id, Seq(structure.Configuration.Compile))
        ),
        Nil, Nil)

      actual must beEqualTo(expected)
    }
    "always extract unmanaged dependencies" in pending
    "extract managed dependencies when supplied" in pending
    "ignore managed dependencies without artifacts or module id" in pending
    "merge configurations in unmanaged and managed dependencies when necessary" in pending
    "correctly extract managed dependencies with classifiers" in pending
    "correctly extract managed dependencies in custom test configurations" in pending
  }
}
