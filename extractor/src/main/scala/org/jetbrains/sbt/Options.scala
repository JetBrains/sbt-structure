package org.jetbrains.sbt

final case class Options(download: Boolean = false,
                         resolveSourceClassifiers: Boolean = false,
                         resolveJavadocClassifiers: Boolean = false,
                         resolveSbtClassifiers: Boolean = false,
                         prettyPrint: Boolean = false,
                         separateProdAndTestSources: Boolean = false)

object Options {

  private val ComaOrSpacesRegex = "(,|\\s+)".r

  /**
   * @param optionsString comma-separated or space-separated list of options, example
   */
  def readFromString(optionsString: String): Options = {
    val options = ComaOrSpacesRegex.split(optionsString).toSeq.map(_.trim).filter(_.nonEmpty)
    readFromSeq(options)
  }

  //noinspection ScalaWeakerAccess (can be used by plugin users)
  def readFromSeq(options: Seq[String]): Options = Options(
    download = options.contains(Keys.Download),
    resolveSourceClassifiers = options.contains(Keys.ResolveSourceClassifiers),
    resolveJavadocClassifiers = options.contains(Keys.ResolveJavadocClassifiers),
    resolveSbtClassifiers = options.contains(Keys.ResolveSbtClassifiers),
    prettyPrint = options.contains(Keys.PrettyPrint),
    separateProdAndTestSources = options.contains(Keys.SeparateProdAndTestSources)
  )

  object Keys {
    val Download = "download"
    val ResolveSourceClassifiers = "resolveSourceClassifiers"
    val ResolveJavadocClassifiers = "resolveJavadocClassifiers"
    val ResolveSbtClassifiers = "resolveSbtClassifiers"
    val PrettyPrint = "prettyPrint"
    val SeparateProdAndTestSources = "separateProdAndTestSources"
  }
}

