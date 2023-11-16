package com.jetbrains.packagesearch.plugin.gradle.tooling;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class PackageSearchGradleModelBuilder extends AbstractModelBuilderService {

    @Override
    public PackageSearchGradleJavaModel buildAll(
            @NotNull String modelName,
            @NotNull Project project,
            @NotNull ModelBuilderContext context
    ) {

        List<PackageSearchGradleJavaModel.Configuration> configurations =
                new ArrayList<>(project.getConfigurations().size());

        for (Configuration configuration : project.getConfigurations()) {
            List<PackageSearchGradleJavaModel.Dependency> dependencies =
                    new ArrayList<>(configuration.getDependencies().size());

            for (Dependency dependency : configuration.getDependencies()) {
                String group = dependency.getGroup();
                String version = dependency.getVersion();

                if (group == null || version == null) continue;

                dependencies.add(new PackageSearchGradleJavaModelImpl.DependencyImpl(group, dependency.getName(), version));
            }

            boolean isCanBeDeclared = true;
            String[] gradleVersion = project.getGradle().getGradleVersion().split("\\.");
            int major = Integer.parseInt(gradleVersion[0]);
            int middle = Integer.parseInt(gradleVersion[1]);
            if (major > 8 || (major == 8 && middle > 2)) {
                isCanBeDeclared = configuration.isCanBeDeclared();
            }

            configurations.add(
                    new PackageSearchGradleJavaModelImpl.ConfigurationImpl(
                            configuration.getName(),
                            dependencies,
                            configuration.isCanBeResolved(),
                            isCanBeDeclared,
                            configuration.isCanBeConsumed()
                    )
            );
        }

        List<String> repositories =
                new ArrayList<>(project.getRepositories().size());

        for (ArtifactRepository repository : project.getRepositories()) {
            if (repository instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepository = (MavenArtifactRepository) repository;
                repositories.add(mavenRepository.getUrl().toString());
            }
        }

        String projectIdentityPath = GradleVersion.current().compareTo(GradleVersion.version("3.3")) >= 0 ?
                ((ProjectInternal) project).getIdentityPath().getPath() : project.getPath();

        String buildFilePath = null;

        if (project.getBuildFile().exists()) {
            buildFilePath = project.getBuildFile().getAbsolutePath();
        }

        return new PackageSearchGradleJavaModelImpl(
                project.getProjectDir().getAbsolutePath(),
                project.getName(),
                project.getRootProject().getName(),
                projectIdentityPath,
                configurations,
                repositories,
                project.getPluginManager().hasPlugin("org.jetbrains.kotlin.jvm"),
                project.getPluginManager().hasPlugin("org.jetbrains.kotlin.multiplatform"),
                project.getPluginManager().hasPlugin("org.jetbrains.kotlin.android"),
                buildFilePath,
                project.getRootProject().getProjectDir().getAbsolutePath()
        );
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(PackageSearchGradleJavaModel.class.getName());
    }

    @Override
    public void reportErrorMessage(
            @NotNull String modelName,
            @NotNull Project project,
            @NotNull ModelBuilderContext context,
            @NotNull Exception exception
    ) {
        context.getMessageReporter()
                .createMessage()
                .withException(exception)
                .withKind(Message.Kind.ERROR)
                .withGroup("gradle.packageSearch")
                .withText("Error while building Package Search Gradle model")
                .reportMessage(project);
    }
}
