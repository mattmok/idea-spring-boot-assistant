import org.jetbrains.changelog.date
import org.jetbrains.changelog.markdownToHTML

plugins {
    java
    id("org.jetbrains.intellij")
    id("org.jetbrains.changelog")
    id("io.freefair.lombok")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

group = "com.tiamaes.cloud.springbootassistant"
version = "1.0.0"

repositories {
    mavenLocal()
    maven {
        isAllowInsecureProtocol = true
        url = uri("http://cloud.tiamaes.com:6001/nexus/repository/public/")
        credentials {
            findProperty("ossrhUsername")?.let {
                username = it as String
            }
            findProperty("ossrhPassword")?.let {
                password = it as String
            }
        }
    }
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("com.miguelfonseca.completely:completely-core:0.9.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")

    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    type.set("IC")
    version.set("2023.2")
    sameSinceUntilBuild.set(false)
    updateSinceUntilBuild.set(false)
    pluginName.set("Tiamaes-Spring-Boot-Assistant")
    plugins.set(listOf("properties", "yaml", "maven", "gradle", "com.intellij.java"))
    jreRepository.set("https://intellij-repository.tiamaes.com/intellij-jbr")
    tasks.runIde.configure {
        jbrVersion.set("17.0.9b1087.7")
    }
}

changelog {
    header.set(provider { "[${version.get()}] - ${date()}" })
}

tasks {
    downloadZipSigner {
        cliPath.set(providers.gradleProperty("intellijMarketSignerPath"))
        cli.set(file(cliPath))
    }

    patchPluginXml {
        sinceBuild.set("232.2")
        untilBuild.set("232.*")
        version.set(
                project.version.toString().run {
                    val pieces = split('-')
                    if (pieces.size > 1) {
                        //if this is not a release version, generate a sub version number from count of hours from 2021-10-01.
                        pieces[0] + "." + (System.currentTimeMillis() / 1000 - 1633046400) / 60 / 60
                    } else {
                        pieces[0]
                    }
                }
        )

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
                projectDir.resolve("README.md").readText().lines().run {
                    val start = "<!-- Plugin description -->"
                    val end = "<!-- Plugin description end -->"

                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end))
                }.joinToString(
                        separator = "\n",
                        postfix = "\nProject [document](https://github.com/mattmok/idea-spring-boot-assistant/#readme)\n"
                ).run { markdownToHTML(this) }
        )

        changeNotes.set(provider {
            changelog.run {
                getOrNull(version.get()) ?: getLatest()
            }.toHTML()
        })
    }

    // See more: https://plugins.jetbrains.com/docs/intellij/plugin-signing.html#signing-methods
    signPlugin {
        certificateChainFile.set(file(providers.gradleProperty("intellijMarketSignerChain").get()))
        privateKeyFile.set(file(providers.gradleProperty("intellijMarketSignerPrivate").get()))
        password.set(providers.gradleProperty("intellijMarketSignerPrivatePassword").get())
    }

    publishPlugin {
        if (!version.toString().contains('-')) {
            dependsOn("patchChangelog")
        }
        token.set(findProperty("intellijPublishToken") as String)
        channels.set(listOf(version.toString().split('-').getOrElse(1) { "default" }.split('.').first()))
    }
}