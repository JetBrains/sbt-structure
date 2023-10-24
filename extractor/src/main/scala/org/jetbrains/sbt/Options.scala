package org.jetbrains.sbt

/**
 * @author Nikolay Obedin
 * @since 10/5/15.
 */
final case class Options(download: Boolean = false,
                         resolveClassifiers: Boolean = false,
                         resolveJavadocs: Boolean = false,
                         resolveSbtClassifiers: Boolean = false,
                         prettyPrint: Boolean = false,
                         insertProjectTransitiveDependencies: Boolean = true)

object Options {

  def readFromString(options: String): Options = Options(
    download = options.contains("download"),
    resolveClassifiers = options.contains("resolveClassifiers"),
    resolveJavadocs = options.contains("resolveJavadocs"),
    resolveSbtClassifiers = options.contains("resolveSbtClassifiers"),
    prettyPrint = options.contains("prettyPrint"),
    insertProjectTransitiveDependencies = options.contains("insertProjectTransitiveDependencies")
  )

  def readFromSeq(options: Seq[String]): Options = Options(
    download = options.contains("download"),
    resolveClassifiers = options.contains("resolveClassifiers"),
    resolveJavadocs = options.contains("resolveJavadocs"),
    resolveSbtClassifiers = options.contains("resolveSbtClassifiers"),
    prettyPrint = options.contains("prettyPrint")
  )
}

