package org.jetbrains.sbt.compat

import sbt.{Configuration, IntegrationTest, Test}

object UtilityTasksCompat {
  val predefinedTestConfigurations: Set[Configuration] = Set(Test, IntegrationTest)
}
