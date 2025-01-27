plugins {
    kotlin("multiplatform") version "2.1.0"
}

group = "com.mylosoftworks"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://www.jitpack.io")
}

dependencies {
    commonMainImplementation("com.github.Mylo-Softworks.Klex:Klex:b962dfca3a")
}

kotlin {
    js {
        browser()
        binaries.executable()
    }
}