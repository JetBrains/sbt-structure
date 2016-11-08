resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("https://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
    Resolver.ivyStylePatterns)
resolvers += Opts.resolver.sonatypeSnapshots

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")
addSbtPlugin("net.virtual-void" % "sbt-cross-building" % "0.8.2-SNAPSHOT")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")
