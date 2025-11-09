plugins {
    id("raindrop.library-conventions")
}

group = "de.jexcellence.translate"
version = "3.0.0"
description = "JExTranslate - Modern i18n API for Spigot/Bukkit/Paper"

ext["vendor"] = "JExcellence"

dependencies {
    compileOnly(libs.paper.api)
    compileOnly("org.jetbrains:annotations:24.1.0")
    
    implementation(libs.adventure.api)
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.serializer.legacy)
    implementation(libs.adventure.serializer.plain)
    implementation("org.yaml:snakeyaml:2.2")
}

tasks {
    jar {
        archiveBaseName.set("jextranslate")
        manifest {
            attributes["API-Version"] = "3.0"
            attributes["Multi-Release"] = "false"
        }
    }

    javadoc {
        (options as StandardJavadocDocletOptions).apply {
            if (JavaVersion.current().isJava9Compatible) {
                addBooleanOption("html5", true)
            }
            addStringOption("tag", "apiNote:a:API Note:")
            addStringOption("tag", "implSpec:a:Implementation Requirements:")
            addStringOption("tag", "implNote:a:Implementation Note:")
        }
        options.memberLevel = JavadocMemberLevel.PUBLIC
    }
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            artifactId = "jextranslate"
            pom {
                url.set("https://github.com/jexcellence/jextranslate")
                developers {
                    developer {
                        id.set("jexcellence")
                        name.set("JExcellence Team")
                        email.set("contact@jexcellence.de")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/jexcellence/jextranslate.git")
                    developerConnection.set("scm:git:ssh://github.com/jexcellence/jextranslate.git")
                    url.set("https://github.com/jexcellence/jextranslate")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jexcellence/jextranslate")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}