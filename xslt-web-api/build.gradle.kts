plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "MERSEL XSLT Service - Web layer (Spring Boot REST API)"

dependencies {
    implementation(project(":xslt-infrastructure"))

    // Spring Boot Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Caffeine cache (token cache)
    implementation(libs.caffeine)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // OpenAPI / Scalar
    implementation(libs.springdoc.openapi.starter)

    // Test
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

springBoot {
    mainClass.set("io.mersel.services.xslt.web.XsltServiceApplication")
}

// Spring Boot bootJar yanında üretilen gereksiz plain JAR'ı devre dışı bırak
tasks.named<Jar>("jar") {
    enabled = false
}
