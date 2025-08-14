package sbt.util

import scala.annotation.{StaticAnnotation, meta}

/**
 * This is a source compatible re-definition of `sbt.util.cacheLevel` which is an sbt 2 concept.
 * In sbt 2, the majority of tasks are expected to be able to be cached. This annotation is a way to opt-out of
 * caching for specific tasks (the annotation is specified on the task definition key).
 *
 * @see [[https://github.com/sbt/sbt/commit/6a7b56a64582e16c6030b302cd23f0a894e2ea57 Default to cached task commit]]
 */
@meta.getter
class cacheLevel(include: Array[AnyRef]) extends StaticAnnotation
