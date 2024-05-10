package com.jetbrains.packagesearch.plugin.gradle.tooling;

import org.gradle.util.GradleVersion;

import java.util.List;

public class PackageSearchGradleJavaModelImpl implements PackageSearchGradleJavaModel {

    private final String projectDir;
    private final List<Configuration> configurations;

    private final List<DeclaredRepository> repositories;
    private final String projectIdentityPath;
    private final String gradleVersion;
    private final boolean isJavaApplied;
    private final boolean isAmperApplied;
    private final boolean isKotlinMultiplatformApplied;
    private final boolean isKotlinAndroidApplied;
    private final String projectName;
    private final String rootProjectName;
    private final String buildFilePath;
    private final String rootProjectPath;

    public PackageSearchGradleJavaModelImpl(
            String projectDir,
            String projectName,
            String rootProjectName,
            String projectIdentityPath,
            List<Configuration> configurations,
            List<DeclaredRepository> repositories,
            boolean isJavaApplied,
            boolean isAmperApplied,
            boolean isKotlinMultiplatformApplied,
            boolean isKotlinAndroidApplied,
            String buildFilePath,
            String rootProjectPath,
            String gradleVersion
    ) {
        this.projectDir = projectDir;
        this.configurations = configurations;
        this.repositories = repositories;
        this.projectIdentityPath = projectIdentityPath;
        this.isJavaApplied = isJavaApplied;
        this.isKotlinMultiplatformApplied = isKotlinMultiplatformApplied;
        this.isKotlinAndroidApplied = isKotlinAndroidApplied;
        this.projectName = projectName;
        this.rootProjectName = rootProjectName;
        this.buildFilePath = buildFilePath;
        this.rootProjectPath = rootProjectPath;
        this.isAmperApplied = isAmperApplied;
        this.gradleVersion = gradleVersion;
    }

    @Override
    public String getGradleVersion() {
        return gradleVersion;
    }

    @Override
    public String getRootProjectPath() {
        return rootProjectPath;
    }

    @Override
    public String getBuildFilePath() {
        return buildFilePath;
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
    public List<DeclaredRepository> getDeclaredRepositories() {
        return repositories;
    }

    @Override
    public boolean isKotlinAndroidApplied() {
        return isKotlinAndroidApplied;
    }

    public boolean isJavaApplied() {
        return isJavaApplied;
    }

    @Override
    public boolean isAmperApplied() {
        return isAmperApplied;
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

    static public class DeclaredRepositoryImpl implements DeclaredRepository {

        private final String url;
        private final String name;

        public DeclaredRepositoryImpl(String url, String name) {
            this.url = url;
            this.name = name;
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public String getName() {
            return name;
        }
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

        private final boolean canBeResolved;
        private final boolean canBeDeclared;
        private final boolean canBeConsumed;

        public ConfigurationImpl(
                String name,
                List<Dependency> dependencies,
                boolean canBeResolved,
                boolean canBeDeclared,
                boolean canBeConsumed
        ) {
            this.name = name;
            this.dependencies = dependencies;
            this.canBeResolved = canBeResolved;
            this.canBeDeclared = canBeDeclared;
            this.canBeConsumed = canBeConsumed;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Dependency> getDependencies() {
            return dependencies;
        }

        @Override
        public boolean isCanBeResolved() {
            return canBeResolved;
        }

        @Override
        public boolean isCanBeDeclared() {
            return canBeDeclared;
        }

        @Override
        public boolean isCanBeConsumed() {
            return canBeConsumed;
        }
    }
}
