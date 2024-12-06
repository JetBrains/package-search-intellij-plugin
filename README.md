# Package Search [![official JetBrains project](https://jb.gg/badges/official-flat-square.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

## Plugin Deprecation Timeline

- **Version for IntelliJ IDEA 2024.3**: is the last functional version of the plugin.
- There will be no version for IntelliJ IDEA 2025.1 or further. The Package Search Plugin and its associated services will be discontinued.

The Package Search web service, including the website and API, will be shut down on **April 1, 2025**. This means that all older versions of plugins will cease functioning after this date. Please plan your transition accordingly.



## Alternatives

IntelliJ IDEA provides some built-in features that can be used instead of the Package Search plugin:

- **[Dependency Analyzer](https://www.jetbrains.com/help/idea/dependency-analyzer.html)** helps you visualize and understand the dependencies in your project.
- IntelliJ IDEA provides auto-completion for dependency coordinates in your Maven build scripts using information from your local Maven repository (`~/.m2/repository`). This allows the IDE to suggest and autocomplete dependencies already present in your local environment or have been used in your projects. It is also possible to download the index of Maven Central locally to get even more options in completion. More details can be found in the documentation on [Maven dependency management](https://www.jetbrains.com/help/idea/maven-dependency-management.html).

---

Thank you for your support over the years.
---

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
