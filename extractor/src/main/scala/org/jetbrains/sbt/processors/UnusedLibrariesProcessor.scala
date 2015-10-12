package org.jetbrains.sbt
package processors

import org.jetbrains.sbt.structure.{ModuleIdentifier, RepositoryData, ProjectData}

/**
 * @author Nikolay Obedin
 * @since 10/6/15.
 */
class UnusedLibrariesProcessor(projectsData: Seq[ProjectData], repositoryData: RepositoryData) {
  private def process(): RepositoryData =
    repositoryData.copy(modules = repositoryData.modules.filter(lib => usedModules.contains(lib.id)))

  private def usedModules: Set[ModuleIdentifier] =
    projectsData.flatMap(_.dependencies.modules.map(_.id)).toSet
}

object UnusedLibrariesProcessor {
  def apply(projectData: Seq[ProjectData])(repositoryData: RepositoryData): RepositoryData =
    new UnusedLibrariesProcessor(projectData, repositoryData).process()
}
