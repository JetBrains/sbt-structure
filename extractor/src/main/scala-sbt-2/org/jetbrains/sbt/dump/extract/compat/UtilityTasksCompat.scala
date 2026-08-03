package org.jetbrains.sbt.dump.extract.compat

import sbt.{Configuration, Test}

object UtilityTasksCompat:
  val predefinedTestConfigurations: Set[Configuration] = Set(Test)
