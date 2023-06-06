package org.jetbrains.packagesearch.plugin.gradle.tooling;

import java.util.List;

public class PackageSearchGradleJavaModelImpl implements PackageSearchGradleJavaModel {

    private final String projectDir;
    private final List<Configuration> configurations;

    private final List<String> repositories;
    private final String projectIdentityPath;

    boolean isKotlinJvmApplied;
    boolean isKotlinMultiplatformApplied;
    boolean isKotlinAndroidApplied;
    private String projectName;

    public PackageSearchGradleJavaModelImpl(
            String projectDir,
            String projectName,
            String projectIdentityPath,
            List<Configuration> configurations,
            List<String> repositoryUrls,
            boolean isKotlinJvmApplied,
            boolean isKotlinAndroidApplied,
            boolean isKotlinMultiplatformApplied
    ) {
        this.projectIdentityPath = projectIdentityPath;
        this.projectDir = projectDir;
        this.configurations = configurations;
        this.repositories = repositoryUrls;
        this.isKotlinJvmApplied = isKotlinJvmApplied;
        this.isKotlinAndroidApplied = isKotlinAndroidApplied;
        this.isKotlinMultiplatformApplied = isKotlinMultiplatformApplied;
        this.projectName = projectName;
    }

    @Override
    public String getProjectDir() {
        return projectDir;
    }

    @Override
    public List<Configuration> getConfigurations() {
        return configurations;
    }

    @Override
    public List<String> getRepositoryUrls() {
        return repositories;
    }

    @Override
    public boolean isKotlinAndroidApplied() {
        return isKotlinAndroidApplied;
    }

    public boolean isKotlinJvmApplied() {
        return isKotlinJvmApplied;
    }

    @Override
    public boolean isKotlinMultiplatformApplied() {
        return isKotlinMultiplatformApplied;
    }

    @Override
    public String getProjectIdentityPath() {
        return projectIdentityPath;
    }

    @Override
    public String getProjectName() {
        return projectName;
    }

    static public class DependencyImpl implements Dependency {

        private final String group;
        private final String artifact;
        private final String version;

        public DependencyImpl(String group, String artifact, String version) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
        }

        @Override
        public String getGroupId() {
            return group;
        }

        @Override
        public String getArtifactId() {
            return artifact;
        }

        @Override
        public String getVersion() {
            return version;
        }
    }

    static public class ConfigurationImpl implements Configuration {

        private final String name;
        private final List<Dependency> dependencies;

        public ConfigurationImpl(String name, List<Dependency> dependencies) {
            this.name = name;
            this.dependencies = dependencies;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Dependency> getDependencies() {
            return dependencies;
        }
    }
}
