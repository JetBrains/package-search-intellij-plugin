package com.jetbrains.packagesearch.plugin.gradle.tooling;

import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

@SuppressWarnings("ALL")
public class PackageSearchGradleModelBuilder extends AbstractPackageSearchGradleModelBuilder {

    @NotNull
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
