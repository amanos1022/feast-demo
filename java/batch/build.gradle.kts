plugins {
  application
}

application {
    mainClass.set("com.music.batch.BatchJobRawEventsProcessor")
}

dependencies {
  implementation("software.amazon.awssdk:s3:2.46.11")
  // Source: https://mvnrepository.com/artifact/software.amazon.awssdk/netty-nio-client
  implementation("software.amazon.awssdk:netty-nio-client:2.46.11")
  implementation("org.duckdb:duckdb_jdbc:1.5.3.0")
  implementation("org.postgresql:postgresql:42.7.5")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.18.4")
  implementation(project(":common"))
}