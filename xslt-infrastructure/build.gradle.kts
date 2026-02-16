plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

description = "MERSEL XSLT Service - Infrastructure layer (Saxon HE implementation)"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(project(":xslt-application"))

    // Saxon HE - XSLT 3.0 / XPath 3.1
    implementation(libs.saxon.he)

    // Caffeine - XSD override cache with TTL
    implementation(libs.caffeine)

    // Spring Boot (DI, Health, Metrics)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Logging
    implementation("org.slf4j:slf4j-api")

    // Test
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
    testImplementation(libs.wiremock.jre8)
    testImplementation(libs.awaitility)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
