# sbt-structure

[![Join the chat at https://gitter.im/JetBrains/sbt-structure](https://badges.gitter.im/JetBrains/sbt-structure.svg)](https://gitter.im/JetBrains/sbt-structure?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Download](https://api.bintray.com/packages/jetbrains/sbt-plugins/sbt-structure-core/images/download.svg) ](https://bintray.com/jetbrains/sbt-plugins/sbt-structure-core/_latestVersion)
[![Build Status](https://travis-ci.org/JetBrains/sbt-structure.svg)](https://travis-ci.org/JetBrains/sbt-structure)

This plugin extracts the structure of an SBT build in XML format. It is used in
Intellij Scala plugin in order to import arbitrary SBT projects into IDEA.

- `sbt-structure-extractor` is SBT plugin that actually extracts information from SBT build

## Problems?

Please report any issues related to sbt-structure in IntelliJ on the [IntelliJ Scala YouTrack project]( https://youtrack.jetbrains.com/issues/SCL).

## Usage

### Core

Add to your `build.sbt`

```scala
resolvers += Resolver.url("jb-bintray", url("http://dl.bintray.com/jetbrains/sbt-plugins"))(Resolver.ivyStylePatterns)

libraryDependencies += "org.jetbrains" %% "sbt-structure-core" % "<version>"
```

Then run extractor or get XML of the structure any other way and deserialize it:

```scala
import org.jetbrains.sbt._
import org.jetbrains.sbt.XmlSerializer._

val structureXml: Elem = XML.load(...)
val structure: Either[Throwable, StructureData] = structureXml.deserialize[StructureData]
```

### Extractor

Extractor can be run as a regular sbt plugin, or loaded into the build during an sbt shell session.

#### As an sbt plugin

The quickest way to try out the extractor is by adding it to your build in `project/plugins.sbt` as an sbt plugin:

```scala
addSbtPlugin("org.jetbrains" % "sbt-structure-extracto" % "<version>")
```

Then from the sbt shell run:

    */*:dump-structure
    
This will output the xml directly to the shell.

To write the xml to a file, run:

    set org.jetbrains.sbt.StrcutureKeys.sbtStructureOptions in Global := "prettyPrint download"
    */*:dumpStructureTo structure.xml
    
The `dumpStructure` task uses the settings described below, the `dumpStructureTo` task takes `sbtStructureFile` as parameter instead.


#### Sideloading

This is the way Intellij-Scala imports projects by default.

Extractor is run in several steps:

- Configure it by defining `sbt-structure-output-file` and
  `sbt-structure-options` settings in `Global` scope.
- Create necessary tasks by applying extractor's jar to your project
- Run `dump-structure` task in `Global` scope

Here is an example of how to run extractor from SBT REPL:

```scala
set SettingKey[Option[File]]("sbtStructureOutputFile") in Global := Some(file("structure.xml"))
set SettingKey[String]("sbtStructureOptions") in Global := "prettyPrint download"
apply -cp <path-to-extractor-jar> org.jetbrains.sbt.CreateTasks
*/*:dump-structure
```

#### Settings

`sbt-structure-options` contains space-separated list of options.
`sbt-structure-output-file` points to a file where structure will be written; if
it is set to `None` then structure will be dump into stdout.

Available options to set in `sbt-structure-options`:

- `download`

  When this option is set extractor will run `update` command for each project in build and build complete
  repository of all transitive library dependencies

- `resolveClassifiers` (requires `download` option to be set as well)

  Same as `download` + downloading sources and javadocs for each transitive library dependency

- `resolveSbtClassifiers`

  This option tells extractor to download sources and javadocs for SBT itself and plugins.

- `prettyPrint`

  This option will force extractor to prettify XML output. Useful for debug purposes.

## Development notes

- Testing against all supported SBT versions can be done with `^ test` command
- Testing against specific version of SBT, for example, 0.13.7: `^^ 0.13.7 test`
- Selected tests can be run with `testOnly` command, e.g. `^ testOnly -- -ex "project name"`

To publish artifacts bump version in `build.sbt` and run in SBT REPL:

```scala
project extractor
^^ 0.12 publish
^^ 0.13 publish
```
   
## Compatibility

Sbt 0.12 is built against Scala 2.9 and will not run on JVM 8. To test for 0.12, you need to run sbt with Java 7
