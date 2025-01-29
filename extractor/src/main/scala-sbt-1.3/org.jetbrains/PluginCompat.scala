package sbt.jetbrains

import sbt.{AttributeKey, Scope, Scoped}

object PluginCompat extends PluginCompatCommonSbt1
  with PluginCompat_SinceSbt_1_3
