lazy val included = RootProject(file("included"))

lazy val root = project.in(file("."))
  .dependsOn(included)

Compile / sourceGenerators += Def.task {
  sys.error("The SBT source task must not generate managed sources")
}.taskValue
