package org.jetbrains.sbt

import org.jetbrains.sbt.structure.StructureData
import sbt.{Def, Keys, Task}
import sbt.jetbrains.SeqOpsCompat._

package object extractors {

  val extractStructure: Def.Initialize[Task[StructureData]] = Def.task {
    StructureData(
      Keys.sbtVersion.value,
      StructureKeys.extractBuilds.value.toSbtSeqType,
      StructureKeys.extractProjects.value.toSbtSeqType,
      StructureKeys.extractRepository.value,
      StructureKeys.localCachePath.value
    )
  }
}
