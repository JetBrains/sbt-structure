# sbt-structure

[![Download](https://api.bintray.com/packages/jetbrains/sbt-plugins/sbt-structure-core/images/download.svg) ](https://bintray.com/jetbrains/sbt-plugins/sbt-structure-core/_latestVersion)
[![Build Status](https://travis-ci.org/JetBrains/sbt-structure.svg)](https://travis-ci.org/JetBrains/sbt-structure)

This plugin is designed to allow one extract complete structure of SBT build in XML format. It is used in
Intellij Scala plugin in order to support importing arbitrary SBT projects into IDEA.

It consists of two parts:

- `sbt-structure-core` is a set of datatypes and functions for their (de)serialization used as internal representation
  of SBT build in sbt-structure
- `sbt-structure-extractor` is SBT plugin that actually extracts information from SBT build

## Usage

### Core

Add to your `build.sbt`

```scala
resolvers += Resolver.url("jb-bintray", url("http://dl.bintray.com/jetbrains/sbt-plugins"))(Resolver.ivyStylePatterns)

libraryDependencies += "org.jetbrains" %% "sbt-structure-core" % "4.1.0" // or later version
```

Then run extractor or get XML of the structure any other way and deserialize it:

```scala
import org.jetbrains.sbt._
import org.jetbrains.sbt.XmlSerializer._

val structureXml: Elem = XML.load(...)
val structure: Either[Throwable, StructureData] = structureXml.deserialize[StructureData]
```

### Extractor

Extractor is run via `apply` command of SBT REPL and configured via `artifactClassifier` and `artifactPath` SBT keys.
In order to run extractor you need to download its jar file, put it somewhere, run SBT REPL and execute in it:

```scala
> set artifactPath := file("structure.xml")
> set artifactClassifier := Some("prettyPrint download")
// Lines above are optional, will be covered later
> apply -cp <path-to-extractor-jar> org.jetbrains.sbt.ReadProject
```

`artifactClassifier` contains space-separated list of options and `artifactPath` points to a file where
structure will be written. If `artifactPath` is not set structure will be printed to stdout.

Available options to set in `artifactClassifier`:

- `download`

  When this option is set extractor will run `update` command for each project in build and build complete
  repository of all transitive library dependencies

- `resolveClassifiers` (requires `download` option to be set as well)

  Same as `download` + downloading sources and javadocs for each transitive library dependency

- `resolveSbtClassifiers`

  This option tells extractor to download sources and javadocs for SBT itself and plugins.

- `prettyPrint`

  This option will force extractor to prettify XML output. Useful for debug purposes.

- `cachedUpdate`

  (Experimental) Enable caching of `update` task results in order to speed up project resolution.

## Development notes

- Testing against all supported SBT versions can be done with `^ test` command
- Testing against specific version of SBT, for example, 0.13.7: `^^ 0.13.7 test`
- Selected tests can be run with `testOnly` command, e.g. `^ testOnly -- -ex "project name"`
- To publish artifacts bump version in `build.sbt` and run in SBT REPL:

   ```scala
   project core
   + publish
   project extractor
   ^^ 0.12 publish
   ^^ 0.13 publish
   ```
