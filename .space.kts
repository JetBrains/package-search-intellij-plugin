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

job("Check IJ snapshot errors") {
    startOn {
        schedule {
            cron("0 8 * * *")
        }
    }

    host("build shadow plugin") {
        env["CI"] = "true"
        shellScript {
            content = "./gradlew :plugin:buildShadowPlugin"
        }
    }
}

job("Publish plugin") {
    startOn { }
    host("Run Gradle") {

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
                version = "300.0.${api.executionNumber()}",
                // automatically update deployment status based on a status of a job
                syncWithAutomationJob = true
            )
        }

        shellScript {
            content = "./gradlew :plugin:publishShadowPlugin"
        }

        kotlinScript { api ->
            val link = File(".").walkTopDown()
                .maxDepth(2)
                .find { it.name == "build-scan-url.txt" }
                ?.readLines()
                ?.first()
            if (link != null) {
                api.space().projects.automation.deployments.update(
                    project = api.projectIdentifier(),
                    targetIdentifier = TargetIdentifier.Key("pkgs-plugin-deploy"),
                    deploymentIdentifier = DeploymentIdentifier.Version("300.0.${api.executionNumber()}"),
                    externalLink = ExternalLink("Build scan", link)
                )
            }
        }
    }
}