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
    private String rootProjectName;


    public PackageSearchGradleJavaModelImpl(
            String projectDir,
            String projectName,
            String rootProjectName,
            String projectIdentityPath,
            List<Configuration> configurations,
            List<String> repositories,
            boolean isKotlinJvmApplied,
            boolean isKotlinMultiplatformApplied,
            boolean isKotlinAndroidApplied
    ) {
        this.projectDir = projectDir;
        this.configurations = configurations;
        this.repositories = repositories;
        this.projectIdentityPath = projectIdentityPath;
        this.isKotlinJvmApplied = isKotlinJvmApplied;
        this.isKotlinMultiplatformApplied = isKotlinMultiplatformApplied;
        this.isKotlinAndroidApplied = isKotlinAndroidApplied;
        this.projectName = projectName;
        this.rootProjectName = rootProjectName;
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

    @Override
    public String getRootProjectName() {
        return rootProjectName;
    }

    public void setRootProjectName(String rootProjectName) {
        this.rootProjectName = rootProjectName;
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
