package org.jetbrains.sbt.integrationTests.utils

import org.jetbrains.sbt.Options.Keys

import scala.collection.mutable

class SbtOptionsBuilder {
  private val options = mutable.ListBuffer[String]()

  private def add(option: String): this.type = {
    options += option
    this
  }

  def sources: this.type = add(Keys.ResolveSourceClassifiers)
  def javadocs: this.type = add(Keys.ResolveJavadocClassifiers)
  def sbtClassifiers: this.type = add(Keys.ResolveSbtClassifiers)
  def separateProdTestSources: this.type = add(Keys.SeparateProdAndTestSources)
  def result: String = options.mkString(" ")
}

object SbtOptionsBuilder {
  def apply(): SbtOptionsBuilder = new SbtOptionsBuilder()

  val ResolveNone: String = ""
  val ResolveNoneAndSeparateProdTestSources: String = apply().separateProdTestSources.result
  val ResolveSources: String = apply().sources.result
  val ResolveJavadocs: String = apply().javadocs.result
  val ResolveSbtClassifiers: String = apply().sbtClassifiers.result
  val ResolveSbtClassifiersAndSeparateProdTestSources: String = apply().sbtClassifiers.separateProdTestSources.result
  val ResolveSourcesAndSbtClassifiers: String = apply().sources.sbtClassifiers.result
  val ResolveSourcesAndSbtClassifiersAndSeparateProdTestSources: String = apply().sources.sbtClassifiers.separateProdTestSources.result
  val ResolveSourcesAndJavaDocsAndSbtClassifiers: String = apply().sources.javadocs.sbtClassifiers.result
}
