import net.ltgt.gradle.errorprone.errorprone
plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("net.ltgt.errorprone") version "4.3.0"
}

group = "com.yodawife"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(26)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.apache.commons:commons-csv:1.12.0")
	implementation("org.webjars.npm:htmx.org:2.0.4")
	implementation("org.webjars.npm:alpinejs:3.14.9")
	implementation("org.webjars:webjars-locator-lite:1.0.1")
	errorprone("com.google.errorprone:error_prone_core:2.44.0")
	errorprone("com.uber.nullaway:nullaway:0.12.7")
	compileOnly("org.jspecify:jspecify:1.0.0")
	testCompileOnly("org.jspecify:jspecify:1.0.0")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	runtimeOnly("org.postgresql:postgresql")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
	options.errorprone.disableAllChecks = true
	options.errorprone.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
	options.errorprone.option("NullAway:AnnotatedPackages", "com.yodawife")
	options.errorprone.option("NullAway:JSpecifyMode", "true")
}

tasks.withType<Test> {
	useJUnitPlatform()
}


