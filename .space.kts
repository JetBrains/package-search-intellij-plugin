job("Publish Snapshots") {
    git {
        refSpec {
            branch("lamberto.basti/dev")
        }
    }
    startOn {
        gitPush {
            pathFilter {
                branch("lamberto.basti/dev")
            }
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

fun RefSpecs.branch(name: String) =
    "/refs/head/${name.removePrefix("/")}".unaryPlus()

fun PathFilter.branch(name: String) =
    "/refs/head/${name.removePrefix("/")}".unaryPlus()
