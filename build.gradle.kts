import econet.deploy.PanelRestartTask
import econet.deploy.SftpDeployTask
import econet.deploy.deploySetting

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

// 배포: deployUpload(SFTP로 plugins/에 올리기) → deployRestart(패널 API로 재시작). 둘을 묶은 것이 deploy.
// 접속 정보는 저장소에 두지 않고 ~/.gradle/gradle.properties나 환경 변수에서 읽는다(buildSrc의 DeploySettings.kt).
// 설정 키가 econet.*인 이유: Econet·SMPUtil·AdminUtil이 같은 서버를 쓰므로 접속 정보 파일 하나를 공유한다.
val pluginJar = if ("shadowJar" in tasks.names) tasks.named<Jar>("shadowJar") else tasks.named<Jar>("jar")

val deployUpload = tasks.register<SftpDeployTask>("deployUpload") {
    group = "deploy"
    description = "Uploads the plugin jar to the server's plugins folder over SFTP, replacing older ${rootProject.name}-*.jar files."
    jar.set(pluginJar.flatMap { it.archiveFile })
    remoteBaseName.set(rootProject.name)
    remoteDirectory.set(deploySetting("econet.sftp.remoteDir").orElse("/plugins"))
    host.set(deploySetting("econet.sftp.host"))
    port.set(deploySetting("econet.sftp.port"))
    user.set(deploySetting("econet.sftp.user"))
    keyPath.set(deploySetting("econet.sftp.keyPath"))
    keyPassphrase.set(deploySetting("econet.sftp.keyPassphrase"))
    password.set(deploySetting("econet.sftp.password"))
    hostKey.set(deploySetting("econet.sftp.hostKey"))
}

val deployRestart = tasks.register<PanelRestartTask>("deployRestart") {
    group = "deploy"
    description = "Restarts the server through the Pterodactyl client API."
    panelUrl.set(deploySetting("econet.panel.url"))
    serverId.set(deploySetting("econet.panel.serverId"))
    apiKey.set(deploySetting("econet.panel.apiKey"))
    mustRunAfter(deployUpload)
}

tasks.register("deploy") {
    group = "deploy"
    description = "Uploads the plugin and restarts the server."
    dependsOn(deployUpload, deployRestart)
}
