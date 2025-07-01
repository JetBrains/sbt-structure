lazy val root = project.in(file("."))
  .aggregate(module1, module2, module3, module4)

lazy val module1 = project.in(file("has-generator1"))
  .settings(
    name := "has-generator1",
    sourceGenerators.in(Compile) += Def.task[Seq[File]] {
      val outputDir = sourceManaged.value
      sys.error("Managed source generation failure")
    }
  )

lazy val module2 = project.in(file("no-generator1"))
  .settings(
    name := "no-generator1"
  )

lazy val module3 = project.in(file("has-generator2"))
  .settings(
    name := "has-generator2",
    sourceGenerators.in(Compile) += Def.task {
      val outputDir = sourceManaged.value
      Seq(outputDir / "MySource2.scala", outputDir / "MySource3.scala")
    }
  )

lazy val module4 = project.in(file("no-generator2"))
  .settings(
    name := "no-generator2"
  )

