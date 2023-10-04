import circlet.pipelines.script.ScriptApi
import java.io.File
import java.time.LocalDate.now

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

job("Publish plugin nightly") {
    startOn {
        schedule {
            // triggers every day, runs only in the master branch
            cron("0 8 * * *")
        }
    }

    host("Run Gradle") {

        env["MAVEN_SPACE_USERNAME"] = "{{ project:jetbrains_team_registry_username }}"
        env["MAVEN_SPACE_PASSWORD"] = "{{ project:jetbrains_team_registry_key }}"
        env["GRADLE_ENTERPRISE_KEY"] = "{{ project:gradle_enterprise_access_key }}"
        env["TOOLBOX_ENTERPRISE_TOKEN"] = "{{ project:toolbox-enterprise-token }}"
        env["CI"] = "true"

        kotlinScript { api ->
            val now = now()
            val pluginSnapshotVersion = "${now.year}.10.${now.dayOfYear}"
            val targetIdentifier = TargetIdentifier.Key("pkgs-plugin-snapshot-deploy")
            api.space().projects.automation.deployments.start(
                project = api.projectIdentifier(),
                targetIdentifier = targetIdentifier,
                version = pluginSnapshotVersion,
                // automatically update deployment status based on a status of a job
                syncWithAutomationJob = true
            )

            val isSuccess = api.runCatchingGradlew {
                task(":plugin:publishShadowPlugin")
                task("publish")
                param("pluginVersion", pluginSnapshotVersion)
            }

            val buildScanLink = File(".").walkTopDown()
                .maxDepth(2)
                .find { it.name == "build-scan-url.txt" }
                ?.readLines()
                ?.first()

            when {
                !isSuccess -> {
                    val channel = ChannelIdentifier.Channel(ChatChannel.FromName("package-search-notifications"))
                    val text = buildString {
                        appendLine("[pkgs-plugin-v2] Build failed: ${api.executionUrl()}")
                        if (buildScanLink != null) {
                            append(" | Build scan: $buildScanLink")
                        }
                    }
                    val content = ChatMessage.Text(text)
                    api.space().chats.messages.sendMessage(channel = channel, content = content)
                    error("Build failed")
                }

                else -> api.space().projects.automation.deployments.update(
                    project = api.projectIdentifier(),
                    targetIdentifier = targetIdentifier,
                    deploymentIdentifier = DeploymentIdentifier.Version(pluginSnapshotVersion),
                    externalLink = buildScanLink?.let { ExternalLink("Build scan", it) }
                )
            }
        }
    }
}

class GradleCommandBuilder {
    private val tasks = mutableListOf<String>()
    private val args = mutableMapOf<String, String>()
    private val params = mutableMapOf<String, String>()

    fun task(name: String) {
        tasks.add(name)
    }

    fun arg(name: String, value: String) {
        args[name] = value
    }

    fun param(name: String, value: String) {
        params[name] = value
    }

    fun build(): List<String> = buildList {
        addAll(tasks)
        args.forEach { (name, value) -> add("-D$name=$value") }
        params.forEach { (name, value) -> add("-P$name=$value") }
    }
}

inline fun ScriptApi.runCatchingGradlew(builder: GradleCommandBuilder.() -> Unit) =
    runCatching { gradlew(*GradleCommandBuilder().apply(builder).build().toTypedArray()) }
        .isSuccess