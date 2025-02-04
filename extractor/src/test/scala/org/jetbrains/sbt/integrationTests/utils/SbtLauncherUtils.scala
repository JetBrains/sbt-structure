package org.jetbrains.sbt.integrationTests.utils

import java.io.File
import java.net.URL

object SbtLauncherUtils {
  def sbtLauncherPath: String = {
    //isolate the launcher in its own folder (otherwise target from this project can be somehow used)
    val launcher = new File("sbt-launcher/sbt-launch.jar")

    if (!launcher.exists())
      sbt.io.Using.urlInputStream(
        new URL(
          "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/1.10.7/sbt-launch-1.10.7.jar"
        )
      ) { in =>
        sbt.io.IO.transfer(in, launcher)
      }

    path(launcher)
  }
}
