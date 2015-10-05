package org.jetbrains.sbt

/**
 * @author Nikolay Obedin
 * @since 10/5/15.
 */
final case class Options(download: Boolean, resolveClassifiers: Boolean, resolveSbtClassifiers: Boolean, cachedUpdate: Boolean)

object Options {
  def readFromString(options: String): Options = Options(
    download = options.contains("download"),
    resolveClassifiers = options.contains("resolveClassifiers"),
    resolveSbtClassifiers = options.contains("resolveSbtClassifiers"),
    cachedUpdate = options.contains("cachedUpdate"))
}

