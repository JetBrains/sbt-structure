package org.jetbrains.sbt.dump

import org.jetbrains.sbt.StructureKeys
import org.jetbrains.sbt.structure.StructureData
import sbt.{Def, Keys, Task}

package object extract {
  val extractStructure: Def.Initialize[Task[StructureData]] = Def.task {
    StructureData(
      Keys.sbtVersion.value,
      StructureKeys.extractBuilds.value,
      StructureKeys.extractProjects.value,
      StructureKeys.extractRepository.value,
      StructureKeys.localCachePath.value
    )
  }

  def invert[K, V](map: Map[K, Seq[V]]): Map[V, Seq[K]] = {
    val tuples: Seq[(V, K)] = for {
      (key, values) <- map.toSeq
      value <- values
    } yield value -> key
    tuples.groupBy(_._1).mapValues(_.map(_._2))
  }
}
