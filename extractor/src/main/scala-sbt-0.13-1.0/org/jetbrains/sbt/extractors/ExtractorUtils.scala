package org.jetbrains.sbt.extractors

import sbt.ProjectRef

/**
  * @author Fernando Cappi
  */
object ExtractorUtils {
  def extractId(projectRef: ProjectRef): String = {
    projectRef.project + ":" + projectRef.build
  }
}
