job("Publish Snapshots") {
    startOn {
        gitPush {
            enabled = true
            anyBranchMatching {
                +"lamberto.basti/dev"
                +"main"
            }
        }
    }
    host("Checkout submodule") {
        shellScript {
            content = "git submodule update --init"
        }
    }
    gradlew("eclipse-temurin:17", "publish") {
        env["IS_SNAPSHOT"] = "true"
        env["MAVEN_SPACE_USERNAME"] = "{{ project:jetbrains_team_registry_username }}"
        env["MAVEN_SPACE_PASSWORD"] = "{{ project:jetbrains_team_registry_key }}"
        env["GRADLE_ENTERPRISE_KEY"] = "{{ project:gradle_enterprise_access_key }}"
        env["CI"] = "true"
    }
}
