job("Publish snapshots") {
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
            content = "./gradlew publish :plugin:publishPlugin"
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
            content = "./gradlew publish :plugin:publishPlugin"
        }
        env["PLUGIN_VERSION"] = "{{ version }}"
        env["IS_SNAPSHOT"] = "true"
        env["MAVEN_SPACE_USERNAME"] = "{{ project:jetbrains_team_registry_username }}"
        env["MAVEN_SPACE_PASSWORD"] = "{{ project:jetbrains_team_registry_key }}"
        env["GRADLE_ENTERPRISE_KEY"] = "{{ project:gradle_enterprise_access_key }}"
        env["TOOLBOX_ENTERPRISE_TOKEN"] = "{{ project:toolbox-enterprise-token }}"
        env["CI"] = "true"
    }

}