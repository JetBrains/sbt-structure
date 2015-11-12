package org.jetbrains.sbt

/**
 * @author Nikolay Obedin
 * @since 10/5/15.
 */
final case class Options(download: Boolean,
                         resolveClassifiers: Boolean,
                         resolveSbtClassifiers: Boolean,
                         cachedUpdate: Boolean,
                         prettyPrint: Boolean)

object Options {
  def readFromString(options: String): Options = Options(
    download = options.contains("download"),
    resolveClassifiers = options.contains("resolveClassifiers"),
    resolveSbtClassifiers = options.contains("resolveSbtClassifiers"),
    cachedUpdate = options.contains("cachedUpdate"),
    prettyPrint = options.contains("prettyPrint")
  )

  def default: Options = Options(
    download = false,
    resolveClassifiers = false,
    resolveSbtClassifiers = false,
    cachedUpdate = false,
    prettyPrint = false
  )
}

