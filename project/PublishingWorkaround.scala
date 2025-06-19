import sbt.Classpaths.{defaultArtifactTasks, defaultPackages, packaged}
import sbt.Keys.*
import sbt.{Artifact, Def, File, IO, Keys, Task, *}

// This is a workaround of https://github.com/sbt/sbt/issues/8166
// and mostly copies the code from sbt.Classpaths
// TODO: delete it once the GitHub issue ^ is fixed
object PublishingWorkaround {

  lazy val publishSbtPluginMavenStyle: Def.Initialize[Task[Boolean]] =
    Def.task(sbtPlugin.value && publishMavenStyle.value)

  /**
   * Produces the Maven-compatible artifacts of an sbt plugin.
   * It adds the sbt-cross version suffix into the artifact names, and it generates a
   * valid POM file, that is a POM file that Maven can resolve.
   */
  def mavenArtifactsOfSbtPlugin: Def.Initialize[Task[Map[Artifact, File]]] =
    Def.task {
      // This is a conditional task. The top-level must be an if expression.
      if (sbt2Plus.value) {
        // Both POMs and JARs are Maven-compatible in sbt 2.x, so ignore the workarounds
        packagedDefaultArtifacts.value
      } else {
        val crossVersion = sbtCrossVersion.value
        val legacyPomArtifact = (makePom / artifact).value
        def addSuffix(a: Artifact): Artifact = a.withName(crossVersion(a.name))

        Map(addSuffix(legacyPomArtifact) -> makeMavenPomOfSbtPlugin.value) ++
          pomConsistentArtifactsForLegacySbt.value ++
          legacyPackagedArtifacts.value
      }
    }

  private lazy val sbt2Plus: Def.Initialize[Boolean] = Def.setting {
    val sbtV = (pluginCrossBuild / sbtBinaryVersion).value
    !sbtV.startsWith("1.") && !sbtV.startsWith("0.")
  }

  private def legacyPackagedArtifacts: Def.Initialize[Task[Map[Artifact, File]]] = Def.task {
    // This is a conditional task. The top-level must be an if expression.
    if (sbtPluginPublishLegacyMavenStyle.value) packagedDefaultArtifacts.value
    else Map.empty[Artifact, File]
  }

  lazy val packagedDefaultArtifacts = packaged(defaultArtifactTasks)

  private def pomConsistentArtifactsForLegacySbt: Def.Initialize[Task[Map[Artifact, File]]] =
    Def.task {
      val crossVersion = sbtCrossVersion.value
      val legacyPackages = packaged(defaultPackages).value
      def copyArtifact(artifact: Artifact, file: File): (Artifact, File) = {
        val nameWithSuffix = crossVersion(artifact.name)
        val targetFile =
          new File(file.getParentFile, file.name.replace(artifact.name, nameWithSuffix))
        IO.copyFile(file, targetFile)
        artifact.withName(nameWithSuffix) -> targetFile
      }
      legacyPackages.map {
        case (artifact, file) => copyArtifact(artifact, file);
      }
    }

  private def sbtCrossVersion: Def.Initialize[String => String] = Def.setting {
    val sbtV = (pluginCrossBuild / sbtBinaryVersion).value
    val scalaV = scalaBinaryVersion.value
    name => name + s"_${scalaV}_$sbtV"
  }

  /**
   * Generates a POM file that Maven can resolve.
   * It appends the sbt cross version into all artifactIds of sbt plugins
   * (the main one and the dependencies).
   */
  private def makeMavenPomOfSbtPlugin: Def.Initialize[Task[File]] = Def.task {
    val config = makePomConfiguration.value
    val nameWithCross = sbtCrossVersion.value(artifact.value.name)
    val version = Keys.version.value
    val pomFile = config.file.get.getParentFile / s"$nameWithCross-$version.pom"
    val publisher = Keys.publisher.value
    val ivySbt = Keys.ivySbt.value
    val module = new ivySbt.Module(moduleSettings.value, appendSbtCrossVersion = true)
    publisher.makePomFile(module, config.withFile(pomFile), streams.value.log)
    pomFile
  }
}
