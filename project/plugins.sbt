resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("https://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
    Resolver.ivyStylePatterns)
resolvers += Opts.resolver.sonatypeSnapshots

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("net.virtual-void" % "sbt-cross-building" % "0.9.0-M1")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")
