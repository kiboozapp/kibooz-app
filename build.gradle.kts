buildscript {
    dependencies {
        constraints {
            // Kover membawa FreeMarker 2.3.32 yang terkena GHSA-27j2-h3m2-8237. Hapus saat Kover sudah memakai versi yang diperbaiki.
            classpath("org.freemarker:freemarker:2.3.35")
        }
    }
}

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.11" apply false
}
