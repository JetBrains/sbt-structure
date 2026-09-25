package org.jetbrains.sbt.compat

import sbt.PluginData
import xsbti.FileConverter

case class FileConverterCompat(underlying: FileConverter)

object FileConverterCompat:
  def forPluginData(pluginData: PluginData): FileConverterCompat =
    FileConverterCompat(pluginData.converter)
