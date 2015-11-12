package org.jetbrains.sbt

import sbt._
import structure._

/**
  * @author Nikolay Obedin
  * @since 11/10/15.
  */
object StructureKeys {
  lazy val sbtStructureOpts         = SettingKey[Options]("ss-options")
  lazy val sbtStructureOptions    = SettingKey[String]("sbt-structure-options")
  lazy val sbtStructureOutputFile = SettingKey[Option[File]]("sbt-structure-output-file")
  lazy val dumpStructure          = TaskKey[Unit]("dump-structure")
}
