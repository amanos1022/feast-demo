plugins {
    application
}

application {
    mainClass.set("com.music.stream.TrackRealTimeJob")
}

dependencies {
    implementation(project(":common"))
    compileOnly("org.apache.flink:flink-streaming-java:2.2.1")
    implementation("org.apache.flink:flink-connector-kafka:5.0.0-2.2")
}

