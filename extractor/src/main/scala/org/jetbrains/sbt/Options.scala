package org.jetbrains.sbt

/**
 * @author Nikolay Obedin
 * @since 10/5/15.
 */
final case class Options(download: Boolean,
                         resolveClassifiers: Boolean,
                         resolveJavadocs: Boolean,
                         resolveSbtClassifiers: Boolean,
                         prettyPrint: Boolean)

object Options {
  def readFromString(options: String): Options = Options(
    download = options.contains("download"),
    resolveClassifiers = options.contains("resolveClassifiers"),
    resolveJavadocs = options.contains("resolveJavadocs"),
    resolveSbtClassifiers = options.contains("resolveSbtClassifiers"),
    prettyPrint = options.contains("prettyPrint")
  )
}

