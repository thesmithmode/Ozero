package ru.ozero.desktop.model

import java.util.Properties

object AppVersion {
    val name: String by lazy {
        val props = Properties()
        javaClass.getResourceAsStream("/version.properties")?.use { props.load(it) }
        props.getProperty("version")
            ?: System.getProperty("jpackage.app-version")
            ?: "dev"
    }
}
