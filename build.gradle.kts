plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    // PaperMC en premier : Maven Central limite les téléchargements (429)
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    // Plugins Eter (EterLib, API des autres plugins) : le jar de leur release GitHub (publiée par la CI à chaque tag)
    ivy {
        url = uri("https://github.com/Eternom/")
        patternLayout { artifact("[module]/releases/download/[revision]/[module]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("com.github.Eternom") }
    }
    // Autres dépendances publiées sur JitPack (VaultAPI...)
    maven("https://jitpack.io") { content { excludeGroup("com.github.Eternom") } }
    // Repli : EterLib publié sur cette machine (`gradlew publishToMavenLocal` dans EterLib), pour tester avant de pousser
    mavenLocal()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")

    // Socle commun : base, Redis, langues, menus, joueurs du réseau, téléportation (plugin EterLib installé sur le serveur)
    compileOnly("com.github.Eternom:EterLib:1.21.0")
    // /invsee et /endersee d'un joueur ailleurs : l'API d'EterSync
    compileOnly("com.github.Eternom:EterSync:1.2.1")
    // Infos du profil (/menu) : l'API des plugins qui les tiennent (facultatifs)
    compileOnly("com.github.Eternom:EterJob:2.0.0")
    compileOnly("com.github.Eternom:EterReward:1.16.0")
    compileOnly("com.github.Eternom:EterSuccess:1.5.0")
    compileOnly("com.github.Eternom:EterClan:1.1.0")
    // « À faire » du /menu : objets dans la boîte des quêtes
    compileOnly("com.github.Eternom:EterQuest:1.1.0")
    // Argent : l'API d'EterEconomy (chaque mouvement avec sa source)
    compileOnly("com.github.Eternom:EterEconomy:2.2.2")

    // Tests : les demandes de tpa (gradlew test), avec EterLib (RedisCache) sur le chemin des tests
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation("com.github.Eternom:EterLib:1.21.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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

// Après chaque build, copie le jar dans <eterPluginsDir>/paper et y supprime l'ancienne version de ce plugin.
// eterPluginsDir se règle dans ~/.gradle/gradle.properties (propre à ta machine) : sans lui, rien n'est copié (JitPack...).
val deployPlugin by tasks.registering(Copy::class) {
    description = "Copie le jar dans le dossier de plugins local (eterPluginsDir)"
    // Variables locales à la tâche : le cache de configuration de Gradle refuse les variables du script
    val eterPluginsDir = providers.gradleProperty("eterPluginsDir")
    val enabled = eterPluginsDir.isPresent
    onlyIf { enabled }
    val jarName = tasks.jar.flatMap { it.archiveBaseName }
    from(tasks.jar)
    into(eterPluginsDir.map { "$it/paper" }.orElse(layout.buildDirectory.dir("deploy").map { it.asFile.path }))
    doFirst {
        destinationDir.listFiles { file -> file.name.startsWith(jarName.get() + "-") && file.name.endsWith(".jar") }
            ?.forEach { it.delete() }
    }
}
tasks.build { finalizedBy(deployPlugin) }

// Publié pour les autres plugins (son API, fr.eternom.eterEssential.api) : compileOnly("com.github.Eternom:EterEssential:<tag>")
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "EterEssential"
            from(components["java"])
        }
    }
}
