plugins {
    id("raindrop.library-conventions")
    id("raindrop.dependencies-yml")
}

group = "de.jexcellence.translate"
version = "3.0.0"
description = "JExTranslate - Modern i18n API for Spigot/Bukkit/Paper"

ext["vendor"] = "JExcellence"

dependencies {
    compileOnly(libs.paper.api)

    compileOnly(libs.jexcommand)
    compileOnly(libs.bundles.jeconfig)
    
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.serializer.legacy)
    compileOnly(libs.adventure.serializer.plain)
    compileOnly(libs.adventure.platform.bukkit)
    compileOnly("org.yaml:snakeyaml:2.2")

    compileOnly(libs.caffeine)
    compileOnly(libs.jackson.core)
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jackson.annotations)
    compileOnly(libs.jackson.jsr310)

    compileOnly(platform(libs.hibernate.platform))
    compileOnly(libs.bundles.hibernate)
    compileOnly(libs.jehibernate)
    
    // Test dependencies
    testImplementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation(libs.paper.api)
}

tasks {
    test {
        useJUnitPlatform()
    }
    
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