package org.jetbrains.sbt.compat

import org.jetbrains.sbt.config.StructureKeys
import sbt.{Keys, Setting}
import xsbti.FileConverter

case class FileConverterCompat(underlying: FileConverter)

object FileConverterCompat:
  lazy val Settings: Seq[Setting[?]] = Seq(
    StructureKeys.fileConverterCompat := FileConverterCompat(Keys.fileConverter.value)
  )
