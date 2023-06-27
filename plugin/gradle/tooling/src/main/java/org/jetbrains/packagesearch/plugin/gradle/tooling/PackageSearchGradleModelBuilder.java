package org.jetbrains.packagesearch.plugin.gradle.tooling;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.util.ArrayList;
import java.util.List;

public class PackageSearchGradleModelBuilder extends AbstractModelBuilderService {

    @Override
    public PackageSearchGradleJavaModel buildAll(
            @NotNull String modelName,
            @NotNull Project project,
            @NotNull ModelBuilderContext context
    ) {

        List<PackageSearchGradleJavaModel.Configuration> configurations =
                new ArrayList<PackageSearchGradleJavaModel.Configuration>(project.getConfigurations().size());

        for (Configuration configuration : project.getConfigurations()) {

            List<PackageSearchGradleJavaModel.Dependency> dependencies =
                    new ArrayList<PackageSearchGradleJavaModel.Dependency>(configuration.getDependencies().size());

            for (Dependency dependency : configuration.getDependencies()) {
                String group = dependency.getGroup();
                String version = dependency.getVersion();

                if (group == null || version == null) continue;

                dependencies.add(new PackageSearchGradleJavaModelImpl.DependencyImpl(group, dependency.getName(), version));
            }
            configurations.add(new PackageSearchGradleJavaModelImpl.ConfigurationImpl(configuration.getName(), dependencies));
        }

        List<String> repositories =
                new ArrayList<String>(project.getRepositories().size());

        for (ArtifactRepository repository : project.getRepositories()) {
            if (repository instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepository = (MavenArtifactRepository) repository;
                repositories.add(mavenRepository.getUrl().toString());
            }
        }

        String projectIdentityPath = GradleVersion.current().compareTo(GradleVersion.version("3.3")) >= 0 ?
                ((ProjectInternal) project).getIdentityPath().getPath() : project.getPath();

        return new PackageSearchGradleJavaModelImpl(
                project.getProjectDir().getAbsolutePath(),
                project.getName(),
                project.getRootProject().getName(),
                projectIdentityPath,
                configurations,
                repositories,
                project.getPluginManager().hasPlugin("org.jetbrains.kotlin.jvm"),
                project.getPluginManager().hasPlugin("org.jetbrains.kotlin.android"),
                project.getPluginManager().hasPlugin("org.jetbrains.kotlin.multiplatform")
        );
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(PackageSearchGradleJavaModel.class.getName());
    }

    @NotNull
    @Override
    public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
        return ErrorMessageBuilder
                .create(project, e, "Gradle import errors")
                .withDescription("Unable to import resolved versions " +
                        "from configurations in project ''${project.name}'' for" +
                        " the Dependencies toolwindow.");
    }
}
