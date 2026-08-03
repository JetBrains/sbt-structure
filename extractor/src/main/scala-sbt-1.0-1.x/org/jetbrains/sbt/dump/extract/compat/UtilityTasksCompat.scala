package org.jetbrains.sbt.dump.extract.compat

import sbt.{Configuration, IntegrationTest, Test}

object UtilityTasksCompat {
  val predefinedTestConfigurations: Set[Configuration] = Set(Test, IntegrationTest)
}
