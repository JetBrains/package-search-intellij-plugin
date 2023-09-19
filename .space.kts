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
