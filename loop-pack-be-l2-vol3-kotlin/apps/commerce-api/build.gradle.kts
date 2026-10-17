plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // pg
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    // Resilience4j 애노테이션이 AOP 프록시로 동작한다. (2026-10-13 결제 설계 6.4 장)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // pg 어댑터 테스트 — 실제 HTTP 로 타임아웃 · 재시도 횟수를 검증한다
    testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}
