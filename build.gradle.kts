plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.paperweight.userdev)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 닉네임 패킷(이름표·탭 목록)에 서버 내부 클래스가 필요해서 paper-api 대신 dev bundle을 쓴다. DESIGN 3장.
    paperweight.paperDevBundle(libs.versions.paper.dev.bundle.get())
    implementation(libs.kotlin.stdlib)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "description" to (project.description ?: ""),
    )
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
    filesMatching("plugin.yml") {
        expand(props)
    }
}
