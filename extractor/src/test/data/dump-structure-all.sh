#! /bin/bash

for D in `find . -mindepth 1 -maxdepth 1 -type d`
do
  (cd "$D"; pwd; sbt \
    -batch \
    -Dsbt.log.noformat=true \
    -Dsbt.version=0.13.13 -Dsbt.global.base=/Users/jast/sbt-structure-global/0.13 \
    -Dsbt.boot.directory=/Users/jast/sbt-structure-global/boot \
    -Dsbt.ivy.home=/Users/jast/sbt-structure-global/ivy2 \
    ';set SettingKey[Option[File]]("sbt-structure-output-file") in Global := Some(file("structure-0.13.13.xml")); set SettingKey[String]("sbt-structure-options") in Global := "prettyPrint download resolveClassifiers resolveSbtClassifiers resolveJavadocs"; apply -cp /Users/jast/.ivy2/local/org.jetbrains/sbt-structure-extractor-0-13-13/scala_2.10/sbt_0.13.13/6.0.3-2-g83a5e99-SNAPSHOT/jars/sbt-structure-extractor-0-13-13.jar org.jetbrains.sbt.CreateTasks; */*:dump-structure')
done
