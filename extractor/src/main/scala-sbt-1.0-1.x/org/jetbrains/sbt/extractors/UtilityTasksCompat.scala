package org.jetbrains.sbt.extractors

import sbt.{Configuration, IntegrationTest, Test}

object UtilityTasksCompat {
  val predefinedTestConfigurations: Set[Configuration] = Set(Test, IntegrationTest)
}
