package org.jetbrains.sbt.extractors

import sbt.{Configuration, Test}

object UtilityTasksCompat:
  val predefinedTestConfigurations: Set[Configuration] = Set(Test)
