package org.jetbrains.sbt.integrationTests.utils

import scala.util.matching.Regex

object RegexSubstitutor {

  private final val SbtMachineSpecificObjectImport: Regex =
    "<import>import \\$(Wrap)?([a-f0-9]+)\\.(.+)</import>".r

  private def matcher(patternMatch: Regex.Match): String = {
    val moduleName = patternMatch.group(3)
    s"<import>import \\$$MachineSpecificHexString.$moduleName</import>"
  }

  def replaceMachineSpecificObjectImports(text: String): String =
    SbtMachineSpecificObjectImport.replaceAllIn(text, matcher _)
}
