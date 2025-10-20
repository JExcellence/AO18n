plugins {
    java
    `maven-publish`
}

group = "de.jexcellence.translate"
version = "3.0.0"
description = "JExTranslate - Modern i18n API for Spigot/Bukkit/Paper"

java {
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    
    implementation("net.kyori:adventure-api:4.16.0")
    implementation("net.kyori:adventure-text-minimessage:4.16.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.16.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.16.0")
    implementation("org.yaml:snakeyaml:2.2")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Xlint:all",
        "-Xlint:-processing",
        "-Xlint:-serial"
    ))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    jvmArgs(
        "-XX:+UseG1GC",
        "-Xmx1G"
    )
}

tasks.jar {
    archiveBaseName.set("jextranslate")
    archiveClassifier.set("")
    
    manifest {
        attributes(
            "Implementation-Title" to "JExTranslate",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "JExcellence",
            "API-Version" to "3.0",
            "Built-By" to System.getProperty("user.name"),
            "Built-JDK" to System.getProperty("java.version"),
            "Created-By" to "Gradle ${gradle.gradleVersion}",
            "Multi-Release" to "false"
        )
    }
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        addStringOption("tag", "apiNote:a:API Note:")
        addStringOption("tag", "implSpec:a:Implementation Requirements:")
        addStringOption("tag", "implNote:a:Implementation Note:")
    }
    options.memberLevel = JavadocMemberLevel.PUBLIC
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = project.group.toString()
            artifactId = "jextranslate"
            version = project.version.toString()
            
            pom {
                name.set("JExTranslate")
                description.set("Modern i18n API for Spigot/Bukkit/Paper with MiniMessage support")
                url.set("https://github.com/jexcellence/jextranslate")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
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

tasks.register("apiJar") {
    description = "Builds a clean API JAR ready for distribution"
    group = "build"
    dependsOn(tasks.jar, tasks.named("sourcesJar"), tasks.named("javadocJar"))
    
    doLast {
        println("=".repeat(60))
        println("API JAR built successfully!")
        println("=".repeat(60))
        println("Main JAR:    ${tasks.jar.get().archiveFile.get().asFile.absolutePath}")
        println("Sources JAR: ${tasks.named<Jar>("sourcesJar").get().archiveFile.get().asFile.absolutePath}")
        println("Javadoc JAR: ${tasks.named<Jar>("javadocJar").get().archiveFile.get().asFile.absolutePath}")
        println("=".repeat(60))
    }
}

tasks.register("cleanBuild") {
    description = "Clean and build the project"
    group = "build"
    dependsOn(tasks.clean, tasks.build)
}

tasks.build {
    dependsOn("apiJar")
}

tasks.register("printVersion") {
    description = "Prints the project version"
    group = "help"
    doLast {
        println("Project: ${project.name}")
        println("Version: ${project.version}")
        println("Group: ${project.group}")
    }
}