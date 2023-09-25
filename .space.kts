import java.io.File

job("Publish jar snapshots") {
    startOn {
        gitPush {
            enabled = true
            anyBranchMatching { +"master" }
        }
    }
    // DO NOT USE THE gradlew FUNCTION!
    // gradlew uses a container which isolates the git configuration
    // of the host machine. Our gradle build interacts with git!
    host("Run Gradle") {
        shellScript {
            content = "./gradlew publish"
        }
        env["IS_SNAPSHOT"] = "true"
        env["MAVEN_SPACE_USERNAME"] = "{{ project:jetbrains_team_registry_username }}"
        env["MAVEN_SPACE_PASSWORD"] = "{{ project:jetbrains_team_registry_key }}"
        env["GRADLE_ENTERPRISE_KEY"] = "{{ project:gradle_enterprise_access_key }}"
        env["TOOLBOX_ENTERPRISE_TOKEN"] = "{{ project:toolbox-enterprise-token }}"
        env["CI"] = "true"
    }
}

job("Publish plugin") {
    parameters {
        text("version")
    }
    startOn { }
    host("Run Gradle") {

        env["PLUGIN_VERSION"] = "{{ version }}"
        env["IS_SNAPSHOT"] = "true"
        env["MAVEN_SPACE_USERNAME"] = "{{ project:jetbrains_team_registry_username }}"
        env["MAVEN_SPACE_PASSWORD"] = "{{ project:jetbrains_team_registry_key }}"
        env["GRADLE_ENTERPRISE_KEY"] = "{{ project:gradle_enterprise_access_key }}"
        env["TOOLBOX_ENTERPRISE_TOKEN"] = "{{ project:toolbox-enterprise-token }}"
        env["CI"] = "true"

        kotlinScript { api ->
            api.space().projects.automation.deployments.start(
                project = api.projectIdentifier(),
                targetIdentifier = TargetIdentifier.Key("pkgs-plugin-deploy"),
                version = api.parameters["version"]!!,
                // automatically update deployment status based on a status of a job
                syncWithAutomationJob = true
            )
        }

        shellScript {
            content = "./gradlew :plugin:publishShadowPlugin"
        }

        kotlinScript { api ->
            val link = File("build-scan-url.txt")
                .takeIf { it.exists() }
                ?.readLines()
                ?.first()
            if (link != null) {
                api.space().projects.automation.deployments.update(
                    project = api.projectIdentifier(),
                    targetIdentifier = TargetIdentifier.Key("pkgs-plugin-deploy"),
                    deploymentIdentifier = DeploymentIdentifier.Version(api.parameters["version"]!!),
                    externalLink = ExternalLink("Build scan", link)
                )
            }
        }
    }

}