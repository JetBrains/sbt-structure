SBT plugin to generate xml project structure
============================================

It has two commands:
* `read-project`: Just returns xml of project structure
* `read-project-and-repository`: Returns xml of project structure, additionally downloads all required dependencies

To publish xml to file, use artifactPath setting.

Plugin usage
-----

Generally this plugin will be used by IntelliJ IDEA SBT plugin. However it's possible to use it by any IDE.
