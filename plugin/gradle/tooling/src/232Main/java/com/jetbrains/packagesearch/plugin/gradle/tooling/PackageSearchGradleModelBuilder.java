package com.jetbrains.packagesearch.plugin.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;

public class PackageSearchGradleModelBuilder extends AbstractPackageSearchGradleModelBuilder {

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
