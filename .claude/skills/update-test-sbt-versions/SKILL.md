---
name: update-test-sbt-versions
description: Updates the latest sbt test versions. Use when the user asks "update the latest sbt versions".
---

Look up all available latest stable releases of sbt at [sbt releases](https://www.scala-sbt.org/download/) and compare
them to the version strings we have in
[LatestSbtVersions.scala](../../../extractor/src/test/scala/org/jetbrains/sbt/integrationTests/utils/LatestSbtVersions.scala).
Only look for latest releases within a certain sbt 1.x series (e.g. the latest release for sbt 1.4 is 1.4.9 and must
remain as is). Ignore sbt 0.13 versions completely. If there are no new versions, don't do anything.

You don't have access to the `gh` command line utility.

If there are indeed new versions which we do not test against, update the file and do the following:

1. Create a new git branch. You may name it something related to the sbt version you are chang
2. Run `sbt --java-home $(java_home -v 17) clean +compile test` to run all tests against the latest sbt releases. This
command will take minutes to execute, so make sure to capture the output so you can examine it, if needed.
3. There should be some test failures. These are expected.
4. Set `CopyActualStructureToExpected = true` in
[ExtractStructureIntegrationTest.scala](../../../extractor/src/test/scala/org/jetbrains/sbt/integrationTests/ExtractStructureIntegrationTest.scala).
5. Run `sbt --java-home $(java_home -v 17) testOnly org.jetbrains.sbt.integrationTests.ExtractStructureIntegrationTest`.
6. There should now be changes to the `structure.xml` files of projects affected by the sbt version change.
7. Set `CopyActualStructureToExpected = false` in
   [ExtractStructureIntegrationTest.scala](../../../extractor/src/test/scala/org/jetbrains/sbt/integrationTests/ExtractStructureIntegrationTest.scala).
It is very important that no changes in this file are ever committed.
8. Run `sbt --java-home $(java_home -v 17) clean +compile test` to run all tests once again.
9. The tests should now all pass.
10. When all tests pass, commit. You are only allowed to commit changes to
[LatestSbtVersions.scala](../../../extractor/src/test/scala/org/jetbrains/sbt/integrationTests/utils/LatestSbtVersions.scala)
and any `structure.xml` files that were created in this process. You may not commit any other changes.
11. Push the changes to the remote.
12. Generate a GitHub Pull Request Title and Description. Mention the changes you made and which test projects were
affected in the description. You may not open the PR yourself, only generate the Title and Description.
13. Done.

If the subcommand `java_home -v 17` fails for some reason, ask the user to provide a JDK 17 path on their machine.
JDK 17 must be used when running sbt.

Additionally, we're in the sbt 2.0.0 Release Candidate phase. New sbt 2.0.0 Release Candidate versions are being
announced on [sbt GitHub releases page](https://github.com/sbt/sbt/releases). Look for the latest sbt 2.0.0 RC release
and update the corresponding version string in
[LatestSbtVersions.scala](../../../extractor/src/test/scala/org/jetbrains/sbt/integrationTests/utils/LatestSbtVersions.scala).
You must not touch the line `val SbtVersion_2_legacy: Version = Version("2.0.0-RC6")`. This version must remain as is
as it is important that we keep testing against that specific version.
