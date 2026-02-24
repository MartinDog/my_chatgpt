plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.mychatgpt"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.0.3"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

dependencies {
    // Spring AI
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-chroma")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // for WebClient (ChromaDbClient)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // PostgreSQL
    implementation("org.postgresql:postgresql")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Apache Tika - for file text extraction
    implementation("org.apache.tika:tika-core:2.9.1")
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.1")

    // Apache POI - for xlsx structured parsing
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Jsoup - for HTML parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
