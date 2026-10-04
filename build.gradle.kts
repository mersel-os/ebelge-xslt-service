plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

// Sürüm, CI/CD'de git tag'inden (-PappVersion=1.2.3) geçirilebilir.
// Verilmezse aşağıdaki varsayılan kullanılır.
// NOT: Aşağıdaki `val defaultAppVersion` satırı scripts/release.sh tarafından
// otomatik bump'lanır; satır formatı korunmalıdır.
val defaultAppVersion = "1.4.0"
val appVersion = (findProperty("appVersion") as String?)?.takeIf { it.isNotBlank() } ?: defaultAppVersion

allprojects {
    group = "io.mersel.services"
    version = appVersion

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21

        // Java Toolchain — doğru JDK yoksa Gradle otomatik indirir
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    apply(plugin = "jacoco")

    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.withType<JacocoCoverageVerification> {
        violationRules {
            rule {
                limit {
                    minimum = "0.50".toBigDecimal()
                }
            }
        }
    }
}

// ── UI Build Tasks ──────────────────────────────────────────────────
// xslt-web-ui is NOT a Gradle module. Build via Exec tasks.
// Skip with: ./gradlew build -PskipUi

val skipUi = project.hasProperty("skipUi")

tasks.register<Exec>("pnpmInstall") {
    enabled = !skipUi
    workingDir = file("xslt-web-ui")
    commandLine("pnpm", "install", "--frozen-lockfile")
    inputs.file("xslt-web-ui/pnpm-lock.yaml")
    outputs.dir("xslt-web-ui/node_modules")
}

tasks.register<Exec>("buildUi") {
    enabled = !skipUi
    dependsOn("pnpmInstall")
    workingDir = file("xslt-web-ui")
    commandLine("pnpm", "build")
    inputs.dir("xslt-web-ui/src")
    inputs.file("xslt-web-ui/vite.config.ts")
    inputs.file("xslt-web-ui/tsconfig.json")
    outputs.dir("xslt-web-ui/dist")
}

tasks.register<Copy>("copyUi") {
    enabled = !skipUi
    dependsOn("buildUi")
    from("xslt-web-ui/dist")
    into("xslt-web-api/src/main/resources/static")
}
