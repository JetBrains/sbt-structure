publishMavenStyle := false

publishTo := Some(Resolver.url("Artifatory Realm", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
