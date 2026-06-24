plugins {
    application
}

application {
    mainClass.set("com.music.producer.PlayEventProducer")
}

dependencies {
    implementation(project(":common"))
}