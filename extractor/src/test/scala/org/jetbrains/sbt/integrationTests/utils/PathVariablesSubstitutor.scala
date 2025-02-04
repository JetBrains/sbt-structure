package org.jetbrains.sbt.integrationTests.utils

import org.jetbrains.sbt.integrationTests.utils.SbtProcessRunner.RunCommonOptions

import java.io.File

class PathVariablesSubstitutor(base: File, runOptions: RunCommonOptions) {

  import runOptions.*

  private lazy val varToPathSubstitutions: Seq[(String, String)] = Seq(
    "$URI_BASE"  -> base.getCanonicalFile.toURI.toString,
    "$BASE"      -> base.getCanonicalPath,
    "$IVY2"      -> sbtIvyHome.getCanonicalPath,
    "$COURSIER"  -> sbtCoursierHome.getCanonicalPath,
    "$SBT_BOOT"  -> sbtBootDir.getCanonicalPath,
    "file:$HOME" -> s"file:${CurrentEnvironment.UserHome.getCanonicalPath}/",
    "$HOME"      -> CurrentEnvironment.UserHome.getCanonicalPath
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
