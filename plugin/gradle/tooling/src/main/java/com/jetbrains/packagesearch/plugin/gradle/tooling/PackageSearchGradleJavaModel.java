package com.jetbrains.packagesearch.plugin.gradle.tooling;

import java.io.Serializable;
import java.util.List;

public interface PackageSearchGradleJavaModel extends Serializable {

    String getProjectDir();
    String getProjectIdentityPath();
    String getProjectName();
    List<Configuration> getConfigurations();
    List<String> getRepositoryUrls();
    String getRootProjectName();
    boolean isJavaApplied();

    boolean isAmperApplied();
    boolean isKotlinAndroidApplied();
    boolean isKotlinMultiplatformApplied();
    String getBuildFilePath();
    String getRootProjectPath();

    interface Configuration extends Serializable {
        String getName();
        List<Dependency> getDependencies();

        boolean isCanBeResolved();
        boolean isCanBeDeclared();
        boolean isCanBeConsumed();
    }

    interface Dependency extends Serializable {
        String getGroupId();
        String getArtifactId();
        String getVersion();
    }

}
