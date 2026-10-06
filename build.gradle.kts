plugins {
    id("java-library")
}

repositories {
    // PaperMC en premier : Maven Central limite les téléchargements (429)
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    // EterLib et VaultAPI : compilés depuis GitHub
    maven("https://jitpack.io")
    // Repli : EterLib publié sur cette machine (`gradlew publishToMavenLocal` dans EterLib), pour tester avant de pousser
    mavenLocal()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")

    // Socle commun : base, Redis, langues, menus, joueurs du réseau, téléportation (plugin EterLib installé sur le serveur)
    compileOnly("com.github.Eternom:EterLib:1.5.0")
    // Économie : /money et /pay passent par Vault (fourni par EterEconomy)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        // Déclarée comme entrée : sinon le cache de Gradle réutilise un plugin.yml avec l'ancienne version
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
