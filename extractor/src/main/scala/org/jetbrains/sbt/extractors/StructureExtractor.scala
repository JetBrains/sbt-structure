package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.structure.StructureData
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

    def dependenciesOf(project: ProjectRef): Seq[ProjectRef] =
      projectSetting(Keys.buildDependencies)(state, project).get.classpathRefs(project)
    val sortedProjects = Dag.topologicalSort(structure.allProjectRefs)(dependenciesOf)

    val (newState, _) = sortedProjects.foldLeft((state, Seq.empty[sbt.Setting[_]])) { (acc, projectRef) =>
        val (intermediateState, previousSettings) = acc
        val extracted = Project.extract(intermediateState)
        projectTask(Keys.update)(intermediateState, projectRef)
        val newSettings = previousSettings :+ (Keys.skip.in(projectRef, Keys.update) := { true })
        val newState = extracted.append(newSettings, intermediateState)
        (newState, newSettings)
    }

    val sbtVersion      = setting(Keys.sbtVersion)(newState).get
    val projectsData    = acceptedProjectRefs.flatMap(new ProjectExtractor(_).extract(newState, options))
    val repositoryData  = new RepositoryExtractor(acceptedProjectRefs).extract(newState, options)
    val localCachePath  = Option(System.getProperty("sbt.ivy.home", System.getProperty("ivy.home")))
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
