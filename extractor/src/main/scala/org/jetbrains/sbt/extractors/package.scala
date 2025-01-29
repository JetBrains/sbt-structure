package org.jetbrains.sbt

import org.jetbrains.sbt.structure.StructureData
import sbt.{Def, Keys, Task}
import scala.collection.Seq
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

  def invert[K, V](map: Map[K, Seq[V]]): Map[V, Seq[K]] = {
    val tuples: Seq[(V, K)] = for {
      (key, values) <- map.toSeq
      value <- values
    } yield value -> key
    tuples.groupBy(_._1).mapValues(_.map(_._2)).toMap
  }
}
