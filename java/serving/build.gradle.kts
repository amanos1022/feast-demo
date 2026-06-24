plugins {
    application
		id("org.springframework.boot") version "4.1.0"
		id("io.spring.dependency-management") version "1.1.7"
}

application {
    mainClass.set("com.example.model_service.ModelServiceApplication")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web:4.1.0")
	testImplementation("org.springframework.boot:spring-boot-starter-test:4.1.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.microsoft.onnxruntime:onnxruntime:1.26.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
