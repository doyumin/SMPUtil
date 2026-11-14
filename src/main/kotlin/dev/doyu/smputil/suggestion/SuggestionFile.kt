package dev.doyu.smputil.suggestion

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

/** `suggestions.yml`. 제안 목록은 여러 값의 모음이라 데이터 폴더의 YAML에 둔다(DESIGN 2장). */
class SuggestionFile(private val file: File) {
    fun load(): List<Suggestion> {
        if (!file.exists()) return emptyList()
        val root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("suggestions") ?: return emptyList()
        return root.getKeys(false).map { key ->
            val section = requireNotNull(root.getConfigurationSection(key)) { "suggestions.$key is not a section" }
            Suggestion(
                id = key.toInt(),
                author = UUID.fromString(requireNotNull(section.getString("author")) { "suggestions.$key.author is missing" }),
                authorName = requireNotNull(section.getString("author-name")) { "suggestions.$key.author-name is missing" },
                text = requireNotNull(section.getString("text")) { "suggestions.$key.text is missing" },
                createdAt = Instant.parse(requireNotNull(section.getString("created-at")) { "suggestions.$key.created-at is missing" }),
            )
        }.sortedBy { it.id }
    }

    /** 임시 파일에 쓰고 옮긴다. 쓰는 도중에 서버가 꺼져도 이전 파일이 남게 하기 위해서다. */
    fun save(suggestions: List<Suggestion>) {
        val yaml = YamlConfiguration()
        for (suggestion in suggestions) {
            val path = "suggestions.${suggestion.id}"
            yaml.set("$path.author", suggestion.author.toString())
            yaml.set("$path.author-name", suggestion.authorName)
            yaml.set("$path.text", suggestion.text)
            yaml.set("$path.created-at", suggestion.createdAt.toString())
        }
        file.parentFile.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        yaml.save(temporary)
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
