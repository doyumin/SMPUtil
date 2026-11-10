package dev.doyu.smputil.home

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** `homes.yml`. 집 목록은 여러 값의 모음이라 PDC가 아니라 데이터 폴더의 YAML에 둔다(DESIGN 2장). */
class HomeFile(private val file: File) {
    fun load(): Map<UUID, Map<String, HomePoint>> {
        if (!file.exists()) return emptyMap()
        val yaml = YamlConfiguration.loadConfiguration(file)
        val root = yaml.getConfigurationSection("homes") ?: return emptyMap()
        return root.getKeys(false).associate { owner ->
            val owned = requireNotNull(root.getConfigurationSection(owner)) { "homes.$owner is not a section" }
            UUID.fromString(owner) to owned.getKeys(false).associateWith { name ->
                val home = requireNotNull(owned.getConfigurationSection(name)) { "homes.$owner.$name is not a section" }
                HomePoint(
                    world = requireNotNull(home.getString("world")) { "homes.$owner.$name.world is missing" },
                    x = home.getDouble("x"),
                    y = home.getDouble("y"),
                    z = home.getDouble("z"),
                    yaw = home.getDouble("yaw").toFloat(),
                    pitch = home.getDouble("pitch").toFloat(),
                )
            }
        }
    }

    /** 임시 파일에 쓰고 옮긴다. 쓰는 도중에 서버가 꺼져도 이전 파일이 남게 하기 위해서다. */
    fun save(homes: Map<UUID, Map<String, HomePoint>>) {
        val yaml = YamlConfiguration()
        for ((owner, owned) in homes) {
            for ((name, home) in owned) {
                val path = "homes.$owner.$name"
                yaml.set("$path.world", home.world)
                yaml.set("$path.x", home.x)
                yaml.set("$path.y", home.y)
                yaml.set("$path.z", home.z)
                yaml.set("$path.yaw", home.yaw.toDouble())
                yaml.set("$path.pitch", home.pitch.toDouble())
            }
        }
        file.parentFile.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        yaml.save(temporary)
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
