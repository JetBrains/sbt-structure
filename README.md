[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Version](https://maven-badges.herokuapp.com/maven-central/org.jetbrains.scala/sbt-structure-extractor/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.jetbrains.scala/sbt-structure-extractor)
![Build Status](https://github.com/jetbrains/sbt-structure/actions/workflows/build.yml/badge.svg)
[![Discord](https://badgen.net/badge/icon/discord?icon=discord&label)](https://discord.gg/aUKpZzeHCK)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/JetBrains/intellij-scala)

# sbt-structure

This plugin extracts the structure of an sbt build in XML format. It is used in
Intellij Scala plugin in order to import arbitrary sbt projects into IDEA.

## Problems?

Please report any issues related to sbt-structure in IntelliJ on the [IntelliJ Scala YouTrack project]( https://youtrack.jetbrains.com/issues/SCL).

## Project structure

- `extractor` is an sbt plugin that actually extracts information from the sbt build.
- `core` is a shared library that is used by both `extractor` and the Intellij Scala plugin.

**_Note:_** `extractor` is packaged as a fat jar, by compiling the sources of `core` and including the output `.class`
files in the resulting jar. This packaging structure is implicitly expected by the Intellij Scala  plugin, due to how
the jar is injected in sbt for extracting the information of a sbt build. The shared sources are contained in the
`shared` directory and linked in the `extractor` and `core` modules, in order to create a source dependency that can be
recognized by IDEA.

## Usage

### Core

Add to your `build.sbt`

```scala
libraryDependencies += "org.jetbrains.scala" %% "sbt-structure-core" % "<version>"
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
addSbtPlugin("org.jetbrains.scala" % "sbt-structure-extractor" % "<version>", "1.3")
```

Then from the sbt shell run:

    */*:dumpStructure
    
This will output the xml directly to the shell.

To write the xml to a file, run:

    set org.jetbrains.sbt.StructureKeys.sbtStructureOptions in Global := "prettyPrint download"
    */*:dumpStructureTo structure.xml
    
The `dumpStructure` task uses the settings described below, the `dumpStructureTo` task takes `sbtStructureFile` as parameter instead.


#### Sideloading

This is the way Intellij-Scala imports projects by default.

Extractor is run in several steps:

- Configure it by defining `sbt-structure-output-file` and
  `sbt-structure-options` settings in `Global` scope.
- Create necessary tasks by applying extractor's jar to your project
- Run `dumpStructure` task in `Global` scope

Here is an example of how to run extractor from sbt REPL:

```scala
set SettingKey[Option[File]]("sbtStructureOutputFile") in Global := Some(file("structure.xml"))
set SettingKey[String]("sbtStructureOptions") in Global := "prettyPrint download"
apply -cp <path-to-extractor-jar> org.jetbrains.sbt.CreateTasks
*/*:dumpStructure
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

  This option tells extractor to download sources and javadocs for sbt itself and plugins.

- `prettyPrint`

  This option will force extractor to prettify XML output. Useful for debug purposes.

## Development notes

- The plugin is cross-built for sbt 0.13 and 1.x  
(see https://www.scala-sbt.org/1.x/docs/Cross-Build-Plugins.html) \
The project is not imported correctly in IntelliJ (some source folders are not detected, etc...) 
This is a known issue, see [correctly handle cross-projects
  ](https://youtrack.jetbrains.com/issue/SCL-12945) 
- Testing against all supported sbt versions can be done with `^ test` command
- Testing against specific version of sbt, for example, 0.13.7: `^^ 0.13.7 test`
- Selected tests can be run with `testOnly` command, e.g. `^ testOnly -- -ex "project name"`

To publish artifacts locally bump version in `build.sbt` and run in sbt REPL:

```
publishAllLocal
```
   
## Compatibility

sbt-structure is built and published for sbt 0.13.x and 1.x

sbt-structure-core is built and published for Scala 2.10, 2.11, 2.12, 2.13
