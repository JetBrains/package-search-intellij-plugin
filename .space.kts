fun PathFilter.include(vararg branches: String) {
    branches.forEach { branch ->
        branch.unaryPlus()
    }
}

job("Publish Snapshots") {
    startOn {
        gitPush {
            pathFilter {
                include("lamberto.basti/dev")
            }
        }
    }
    gradlew("eclipse-temurin:17", ":plugins:core:publish") {
        env["IS_SNAPSHOT"] = "true"
        env["MAVEN_SPACE_USERNAME"] = "{{ project:jetbrains_team_registry_username }}"
        env["MAVEN_SPACE_PASSWORD"] = "{{ project:jetbrains_team_registry_key }}"
    }
}