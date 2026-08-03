package org.jetbrains.sbt.compat

import sbt.{Configuration, Test}

object UtilityTasksCompat:
  val predefinedTestConfigurations: Set[Configuration] = Set(Test)
