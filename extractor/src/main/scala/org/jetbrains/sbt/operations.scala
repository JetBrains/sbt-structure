package org.jetbrains.sbt

/** Compatibility facades for sbt runtime helpers now implemented in [[org.jetbrains.sbt.runtime]]. */
trait SbtStateOps extends runtime.SbtStateOps
trait TaskOps extends runtime.TaskOps
trait ModulesOps extends runtime.ModulesOps
