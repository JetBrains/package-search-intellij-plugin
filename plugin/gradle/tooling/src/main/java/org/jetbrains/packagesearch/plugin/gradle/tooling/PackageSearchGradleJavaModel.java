package org.jetbrains.packagesearch.plugin.gradle.tooling;

import java.io.Serializable;
import java.util.List;

public interface PackageSearchGradleJavaModel extends Serializable {

    String getProjectDir();
    List<Configuration> getConfigurations();

    List<String> getRepositoryUrls();

    boolean isKotlinJvmApplied();
    boolean isKotlinAndroidApplied();
    boolean isKotlinMultiplatformApplied();

    interface Configuration extends Serializable {
        String getName();
        List<Dependency> getDependencies();
    }

    interface Dependency extends Serializable {
        String getGroupId();
        String getArtifactId();
        String getVersion();
    }

}
