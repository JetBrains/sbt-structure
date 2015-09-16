package org.jetbrains.sbt.extractors

import org.jetbrains.sbt.structure.{ProjectData, StructureData}
import sbt._

//import scala.language.reflectiveCalls

object StructureExtractor extends Extractor {

  override type Data = StructureData

  def extract(implicit state: State, options: Extractor.Options): Option[Data] = {
    val acceptedProjectRefs =
      structure.allProjectRefs.filter { case ref @ ProjectRef(_, id) =>
        val projectAccepted = structure.allProjects.find(_.id == id).exists(areNecessaryPluginsLoaded)
        val shouldSkipProject =
          setting(SettingKeys.ideSkipProject.in(ref)).getOrElse(false) ||
            setting(SettingKeys.sbtIdeaIgnoreModule.in(ref)).getOrElse(false)
        projectAccepted && !shouldSkipProject
      }

    val allProjectData = taskKey[Seq[ProjectData]]("All project data")
    def settings(acceptedProjectRefs: Seq[ProjectRef]) = allProjectData := {
      ProjectExtractor.projectData.all(ScopeFilter(inProjects(acceptedProjectRefs: _*))).map(_.flatten).value
    }

    val extracted = Project.extract(state)
    val session = extracted.session
    val allSettings = acceptedProjectRefs.map { currentRef =>
      Load.transformSettings(Load.projectScope(currentRef), currentRef.build, extracted.rootProject, Seq(ProjectExtractor.projectExtractTask(options)(state, currentRef)))
    }
    val rootSettings = Load.transformSettings(Load.projectScope(extracted.currentRef), extracted.currentRef.build, extracted.rootProject, Seq(settings(acceptedProjectRefs)))
    val newStructure = Load.reapply(session.original ++ allSettings.flatten ++ rootSettings, structure)(extracted.showKey)
    val newState = Project.setProject(session, newStructure, state)

    val sbtVersion = setting(Keys.sbtVersion).get
    val projectsData = task(allProjectData)(newState).get
    val repositoryData = if (options.download) new RepositoryExtractor(acceptedProjectRefs).extract(newState, options) else None
    val localCachePath = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))
    Some(StructureData(sbtVersion, projectsData, repositoryData, localCachePath))
  }

  private def areNecessaryPluginsLoaded(project: ResolvedProject): Boolean = {
    // Here is a hackish way to test whether project has JvmPlugin enabled.
    // Prior to 0.13.8 SBT had this one enabled by default for all projects.
    // Now there may exist projects with IvyPlugin (and thus JvmPlugin) disabled
    // lacking all the settings we need to extract in order to import project in IDEA.
    // These projects are filtered out by checking `autoPlugins` field.
    // But earlier versions of SBT 0.13.x had no `autoPlugins` field so
    // structural typing is used to get the data.
    try {
      type ResolvedProject_0_13_7 = {def autoPlugins: Seq[{ def label: String}]}
      val resolvedProject_0_13_7 = project.asInstanceOf[ResolvedProject_0_13_7]
      val labels = resolvedProject_0_13_7.autoPlugins.map(_.label)
      labels.contains("sbt.plugins.JvmPlugin")
    } catch {
      case _ : NoSuchMethodException => true
    }
  }
}
