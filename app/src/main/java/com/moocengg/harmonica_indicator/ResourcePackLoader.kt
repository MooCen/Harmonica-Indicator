package com.moocengg.harmonica_indicator

import android.content.Context
import android.util.Log
import com.moocengg.harmonica_indicator.ResourcePackLoader.current
import java.io.File
import java.io.InputStream

/**
 * 资源包的完整元信息，字段名与 info.yml 中的 key 一一对应。
 * 缺失的 key 使用默认值。
 */
data class ResourcePackInfo(
    val name: String = "",
    val author: String = "",
    val description: String = "",
    val hitFx: List<Int> = listOf(1, 1),
    val holdAtlas: List<Int> = emptyList(),
    val holdAtlasMH: List<Int> = emptyList(),
    val hitFxDuration: Float = 0f,
    val hitFxScale: Float = 1f,
    val colorPerfect: Int? = null,
    val colorGood: Int? = null,
) {
    /** hitFx 的第 0 个值，即列数 */
    val hitFxCols: Int get() = hitFx.getOrElse(0) { 1 }
    /** hitFx 的第 1 个值，即行数 */
    val hitFxRows: Int get() = hitFx.getOrElse(1) { 1 }
}


object ResourcePackLoader {

    private const val TAG = "ResourcePackLoader"
    private const val ASSETS_ROOT = "resource_packs_phigros_theme"
    private const val INFO_FILE = "info.yml"
    private const val HITFX_FILE = "hit_fx.png"
    private const val EXTERNAL_ROOT = "resource_packs"

    /** 当前已加载的资源包信息。切换资源包时自动更新。 */
    var current: ResourcePackInfo = ResourcePackInfo()
        private set

    /** 读取指定资源包的 info.yml，并更新 [current]。失败返回 null。 */
    fun load(context: Context, packName: String): ResourcePackInfo? {
        val stream = openPackFile(context, packName, INFO_FILE) ?: run {
            Log.e(TAG, "读取资源包配置失败: $packName/$INFO_FILE")
            return null
        }
        val info = try {
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                parseInfoYml(reader.readText())
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 info.yml 失败: $packName", e)
            null
        }
        if (info != null) current = info
        return info
    }
    /*fun load(context: Context, packName: String): ResourcePackInfo? {
        val path = "$ASSETS_ROOT/$packName/$INFO_FILE"
        val info = try {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { reader ->
                parseInfoYml(reader.readText())
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取资源包配置失败: $path", e)
            null
        }
        if (info != null) current = info
        return info
    }*/

    /** 打开特效精灵图。失败返回 null。 */
    fun openHitFxImage(context: Context, packName: String): InputStream? =
        openPackFile(context, packName, HITFX_FILE)
    /*fun openHitFxImage(context: Context, packName: String): InputStream? {
        val path = "$ASSETS_ROOT/$packName/$HITFX_FILE"
        return try {
            context.assets.open(path)
        } catch (e: Exception) {
            Log.e(TAG, "打开特效图片失败: $path", e)
            null
        }
    }*/

    // ========================= 解析 =========================

    private fun parseInfoYml(content: String): ResourcePackInfo {
        val map = parseSimpleYaml(content)
        return ResourcePackInfo(
            name = map["name"].orEmpty().unquote(),
            author = map["author"].orEmpty().unquote(),
            description = map["description"].orEmpty().unquote(),
            hitFx = map["hitFx"]?.parseIntArray() ?: listOf(1, 1),
            holdAtlas = map["holdAtlas"]?.parseIntArray() ?: emptyList(),
            holdAtlasMH = map["holdAtlasMH"]?.parseIntArray() ?: emptyList(),
            hitFxDuration = map["hitFxDuration"]?.toFloatOrNull() ?: 0f,
            hitFxScale = map["hitFxScale"]?.toFloatOrNull() ?: 1f,
            colorPerfect = map["colorPerfect"]?.parseHexColor(),
            colorGood = map["colorGood"]?.parseHexColor(),
        )
    }

    /**
     * 极简 YAML 解析：只处理顶层 `key: value`，不处理嵌套、多行字符串、列表块。
     * 支持整行 "#" 注释、值两端引号、值里含冒号（通过引号包裹或全角冒号规避）。
     */
    private fun parseSimpleYaml(content: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val colonIdx = findColonOutsideQuotes(line)
            if (colonIdx < 0) continue
            val key = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
        return map
    }

    /** 找第一个不在引号内的英文冒号，避免 `name: "a:b"` 被切错 */
    private fun findColonOutsideQuotes(line: String): Int {
        var inQuote = false
        var quoteChar = ' '
        for (i in line.indices) {
            val c = line[i]
            if (inQuote) {
                if (c == quoteChar) inQuote = false
            } else {
                when (c) {
                    '"', '\'' -> { inQuote = true; quoteChar = c }
                    ':' -> return i
                }
            }
        }
        return -1
    }

    private fun String.unquote(): String {
        if (length >= 2 &&
            ((first() == '"' && last() == '"') || (first() == '\'' && last() == '\''))
        ) return substring(1, length - 1)
        return this
    }

    /** 解析 "[8, 8]" 或 "8,8" 形式的整型数组 */
    private fun String.parseIntArray(): List<Int> {
        val inner = trim().removePrefix("[").removeSuffix("]")
        return inner.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    /** 解析 "0xF5A3D0" 或十进制数字形式的颜色。失败返回 null。 */
    private fun String.parseHexColor(): Int? = try {
        val s = trim()
        if (s.startsWith("0x", ignoreCase = true)) {
            val rgb = s.substring(2).toLong(16)
            // 补上不透明的 alpha
            (0xFF000000L or rgb).toInt()
        } else s.toInt()
    } catch (e: Exception) {
        null
    }


    /**
     * 枚举所有可用的资源包：assets 里的 + getExternalFilesDir 里导入的。
     * 返回按字母排序、去重后的包名列表。
     */
    fun listAllPacks(context: Context): List<String> {
        val result = mutableSetOf<String>()

        // ① assets 里的
        try {
            context.assets.list(ASSETS_ROOT)?.forEach { name ->
                if (name.isNotBlank() && !name.startsWith(".")) result.add(name)
            }
        } catch (e: Exception) {
            Log.w(TAG, "枚举 assets 资源包失败", e)
        }

        // ② getExternalFilesDir 里导入的（必须含 info.yml 才算有效）
        val extRoot = File(context.getExternalFilesDir(null), EXTERNAL_ROOT)
        if (extRoot.exists() && extRoot.isDirectory) {
            extRoot.listFiles()?.forEach { f ->
                if (f.isDirectory && File(f, INFO_FILE).exists()) {
                    result.add(f.name)
                }
            }
        }

        val list = result.sorted()
        Log.i(TAG, "枚举资源包：$list")
        return list
    }
    /**
     * 只枚举用户导入的外部资源包（getExternalFilesDir 里的），
     * 用于「管理资源包」界面。内置的 phi 包不算在内。
     */
    fun listExternalPacks(context: Context): List<String> {
        val extRoot = File(context.getExternalFilesDir(null), EXTERNAL_ROOT)
        if (!extRoot.exists() || !extRoot.isDirectory) return emptyList()
        return extRoot.listFiles()
            ?.filter { it.isDirectory && File(it, INFO_FILE).exists() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    // ================ 调用 ===================

    /**
     * 打开资源包里的某个文件（相对路径）。
     * 先查 external（用户导入的），查不到再回退 assets（内置的）。
     */
    fun openPackFile(context: Context, packName: String, relativePath: String): InputStream? {
        // ① 外部目录
        val extFile = File(
            context.getExternalFilesDir(null),
            "$EXTERNAL_ROOT/$packName/$relativePath"
        )
        if (extFile.exists() && extFile.isFile) {
            return try {
                extFile.inputStream()
            } catch (e: Exception) {
                Log.w(TAG, "打开外部文件失败: ${extFile.absolutePath}", e)
                null
            }
        }
        // ② assets
        val assetPath = "${ASSETS_ROOT}/$packName/$relativePath"
        return try {
            context.assets.open(assetPath)
        } catch (e: Exception) {
            Log.w(TAG, "打开 assets 失败: $assetPath", e)
            null
        }
    }

}