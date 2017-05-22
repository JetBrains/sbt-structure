import sbt._, Keys._

sealed trait SbtVersionSeries
case object Sbt012 extends SbtVersionSeries
case object Sbt013 extends SbtVersionSeries
case object Sbt1   extends SbtVersionSeries

/**
  * Hack to fix sbt version when cross-building sbt versions
  */
object CrossBuildingPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val sbtPartV: SettingKey[Option[(Int, Int)]] = settingKey[Option[(Int, Int)]]("")
    val sbtVersionSeries: SettingKey[SbtVersionSeries] = settingKey[SbtVersionSeries]("")
  }
  import autoImport._

  override def globalSettings = Seq(
    sbtPartV := CrossVersion partialVersion (sbtVersion in pluginCrossBuild).value,
    sbtVersionSeries := (sbtPartV.value match {
      case Some((0, 12)) => Sbt012
      case Some((0, 13)) => Sbt013
      case Some((1, _))  => Sbt1
      case _             => sys error s"Unhandled sbt version ${(sbtVersion in pluginCrossBuild).value}"
    })
  )
}
