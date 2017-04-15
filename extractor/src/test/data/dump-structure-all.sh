#! /bin/bash

SBT_VERSION="0.13.13"
SBT_STRUCTURE_VERSION="7.0.0-14-gecc0b59-SNAPSHOT"
SBT_DASHED=$(echo "${SBT_VERSION}" | tr . -)
SBT_STRUCTURE_PKG="sbt-structure-extractor-${SBT_DASHED}"

for D in `find . -mindepth 1 -maxdepth 1 -type d`
do
  echo "dumping in ${D}"
  (cd "$D"; pwd; sbt \
    -batch \
    -Dsbt.log.noformat=true \
    -Dsbt.version=${SBT_VERSION} -Dsbt.global.base=/Users/jast/.sbt-structure-global/0.13 \
    -Dsbt.boot.directory=/Users/jast/.sbt-structure-global/boot \
    -Dsbt.ivy.home=/Users/jast/.sbt-structure-global/ivy2 \
    ";set Seq(SettingKey[Option[File]](\"sbt-structure-output-file\") in Global := Some(file(\"structure-${SBT_VERSION}.xml\")), SettingKey[String](\"sbt-structure-options\") in Global := \"prettyPrint download resolveClassifiers resolveSbtClassifiers resolveJavadocs\"); apply -cp /Users/jast/.ivy2/local/org.jetbrains/${SBT_STRUCTURE_PKG}/scala_2.10/sbt_${SBT_VERSION}/${SBT_STRUCTURE_VERSION}/jars/${SBT_STRUCTURE_PKG}.jar org.jetbrains.sbt.CreateTasks; */*:dump-structure")
done
