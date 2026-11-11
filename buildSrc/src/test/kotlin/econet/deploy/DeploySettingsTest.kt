package econet.deploy

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DeploySettingsTest {
    @Test
    fun `property keys map to upper snake case environment variables`() {
        assertEquals("ECONET_SFTP_HOST", environmentNameOf("econet.sftp.host"))
        assertEquals("ECONET_SFTP_KEY_PATH", environmentNameOf("econet.sftp.keyPath"))
        assertEquals("ECONET_PANEL_SERVER_ID", environmentNameOf("econet.panel.serverId"))
        assertEquals("ECONET_PANEL_API_KEY", environmentNameOf("econet.panel.apiKey"))
    }

    @Test
    fun `sftp addresses accept the panel url form`() {
        assertEquals(SftpAddress("sftp.example.com", 2022), parseSftpAddress("sftp://sftp.example.com:2022"))
        assertEquals(SftpAddress("sftp.example.com", null), parseSftpAddress("sftp.example.com"))
        assertFailsWith<GradleException> { parseSftpAddress("sftp://host:99999") }
        assertFailsWith<GradleException> { parseSftpAddress("sftp://") }
    }

    // 세 플러그인(Econet, SMPUtil, AdminUtil)이 같은 plugins 폴더를 쓴다. 한 플러그인의 배포가 형제의 파일을 지우면 그 플러그인이 조용히 사라진다.
    private val siblings = listOf("Econet.jar", "AdminUtil.jar", "Econet-1.0.jar", "AdminUtil-2.1.jar", "Vault.jar", "LuckPerms-5.4.jar")

    @Test
    fun `deploying SMPUtil removes only its own jars and unfinished uploads`() {
        val names = listOf(
            "SMPUtil.jar", "SMPUtil.jar.uploading", "SMPUtil-1.0.jar", "SMPUtil-0.9.jar", "SMPUtil-1.0.jar.uploading",
        ) + siblings

        assertEquals(
            listOf("SMPUtil-0.9.jar", "SMPUtil-1.0.jar", "SMPUtil-1.0.jar.uploading", "SMPUtil.jar", "SMPUtil.jar.uploading"),
            ownedRemoteFiles(names, "SMPUtil"),
        )
    }

    @Test
    fun `deploying SMPUtil leaves Econet and AdminUtil jars in place`() {
        val names = listOf("SMPUtil.jar") + siblings

        val doomed = ownedRemoteFiles(names, "SMPUtil")

        assertEquals(listOf("SMPUtil.jar"), doomed)
        for (sibling in siblings) assertFalse(sibling in doomed, "$sibling must survive an SMPUtil deploy")
    }

    @Test
    fun `names that only look similar are not touched`() {
        val names = listOf(
            "SMPUtilAddon.jar", "SMPUtilAddon-1.0.jar", "SMPUtil", "SMPUtil-notes.txt", "SMPUtil.jar.bak", "smputil.jar", "SMPUtil.yml",
            "SMPUtil/SMPUtil.jar",
        )

        assertEquals(emptyList(), ownedRemoteFiles(names, "SMPUtil"))
    }

    @Test
    fun `the same rule protects SMPUtil when Econet or AdminUtil is deployed`() {
        val names = listOf("SMPUtil.jar", "SMPUtil-1.0.jar", "Econet.jar", "AdminUtil.jar")

        assertEquals(listOf("Econet.jar"), ownedRemoteFiles(names, "Econet"))
        assertEquals(listOf("AdminUtil.jar"), ownedRemoteFiles(names, "AdminUtil"))
    }
}
