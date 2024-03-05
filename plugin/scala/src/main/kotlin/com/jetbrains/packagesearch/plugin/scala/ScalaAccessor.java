package com.jetbrains.packagesearch.plugin.scala;

import com.intellij.openapi.module.Module;
import org.jetbrains.plugins.scala.ScalaVersion;
import scala.Option;

public class ScalaAccessor {
    public static Option<ScalaVersion> getScalaVersion(Module module) {
        return org.jetbrains.plugins.scala.project.package$.MODULE$.ModuleExt(module).scalaMinorVersion();
    }
}
