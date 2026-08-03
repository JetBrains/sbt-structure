package org.jetbrains.sbt.dump

import org.jetbrains.sbt.StructureKeys
import org.jetbrains.sbt.structure.StructureData
import sbt.{Def, Keys, Task}
import sbt.jetbrains.SeqOpsCompat._

package object extract {

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
