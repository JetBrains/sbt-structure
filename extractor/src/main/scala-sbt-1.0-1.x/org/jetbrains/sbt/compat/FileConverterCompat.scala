package org.jetbrains.sbt.compat

import sbt.Setting
import org.jetbrains.sbt.config.StructureKeys
import sbt.internal.BuildUnit

/**
 * This class exists to pretend that we have a `xsbti.FileConverter`. It is an interface which exists since sbt 1.4.x.
 * `sbt-structure` compiles against sbt 1.3, so we cannot/don't use it in any of our code.
 *
 * However, `xsbti.FileConverter` is very important in sbt 2, and the Scala 3/sbt 2 definition of `FileConverterCompat`
 * exposes an underlying instance of `xsbti.FileConverter`.
 */
case class FileConverterCompat()

object FileConverterCompat {
  lazy val Settings: Seq[Setting[?]] = Seq(
    StructureKeys.fileConverterCompat := FileConverterCompat()
  )

  def forBuildUnit(unit: BuildUnit): FileConverterCompat = FileConverterCompat()
}
