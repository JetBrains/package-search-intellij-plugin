# Package Search [![official JetBrains project](https://jb.gg/badges/official-flat-square.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

**\[DEPRECATED\]** This plugin has been deprecated. No further development will be done on this plugin.

Package Search is an IntelliJ plugin that allows you to search for packages from the editor. It supports searching for
packages from the following package managers by default:

- [Maven](https://maven.apache.org/)
- [Gradle](https://gradle.org/)
- [Amper](https://blog.jetbrains.com/blog/2023/11/09/amper-improving-the-build-tooling-user-experience/)

It also supports Kotlin Multiplatform projects for both for Gradle and Amper.

![Package Search](https://plugins.jetbrains.com/files/12507/screenshot_2cd70867-8304-496a-a023-a052b01e24f6)
![Package Search](https://plugins.jetbrains.com/files/12507/screenshot_2314f197-0e0a-4b45-bdbe-23d2bf745df2)

# Installation

You can download the plugin from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/12507-package-search)
or directly in IntelliJ by going to `Preferences > Plugins > Marketplace` and searching for `Package Search`.

The plugin is compatible with IntelliJ 2023.2 and newer.

# Building

To build the plugin, run the following command:

```shell
./gradlew :plugin:buildShadowPlugin
```

To run the plugin, run the following command:

```shell
./gradlew :plugin:runIde
```
