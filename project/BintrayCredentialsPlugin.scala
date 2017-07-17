import java.io.File

import bintray.BintrayPlugin
import bintray.BintrayPlugin.autoImport.bintrayCredentialsFile
import sbt._, Keys._

object BintrayCredentialsPlugin extends AutoPlugin {

  override def requires = BintrayPlugin

  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val bintrayDumpCredentials: TaskKey[Option[File]] = taskKey[Option[File]]("dump bintray credentials read from environment vars to file. For use in CI.")
  }

  import autoImport._

  override def globalSettings = Seq(
    bintrayDumpCredentials := {

      val credsFile = bintrayCredentialsFile.value

      if (credsFile.isFile) {
        streams.value.log.info(s"bintray credentials file already exists at $credsFile")
        Option(credsFile)
      } else {
        val dumped = for {
          user <- sys.env.get("BINTRAY_USER")
          key <- sys.env.get("BINTRAY_PASS")
        } yield {
          val credentials =
            s"""
               |realm = Bintray API Realm
               |host = api.bintray.com
               |user = $user
               |password = $key
        """.stripMargin

          IO.write(credsFile, credentials)
          credsFile
        }

        assert(credsFile.isFile, s"Bintray credentials not created. Are BINTRAY_USER and BINTRAY_KEY defined?")
        dumped
      }
    }
  )
}
