package com.moocengg.harmonica_indicator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

object ResourcePackImporter {

    private const val TAG = "ResourcePackImporter"
    private const val ASSETS_ROOT = "resource_packs_phigros_theme"
    private const val EXTERNAL_ROOT = "resource_packs"

    /** 导入结果 */
    sealed class Result {
        data class Success(val packName: String, val fileCount: Int, val targetDir: File) : Result()
        data class Failure(val reason: String) : Result()
    }

    /**
     * 从 Uri 读取 zip 并解压到 getExternalFilesDir(null)/resource_packs/{packName}/
     * 包名 = zip 文件名去掉 .zip
     * 冲突（外部或 assets 已存在同名）则拒绝
     * 注意：此函数是阻塞的，必须放在后台线程调用
     */
    fun importFromZip(context: Context, uri: Uri): Result {
        // ① 拿文件名 → 包名
        val displayName = queryDisplayName(context, uri)
            ?: return Result.Failure("无法读取文件名")
        val packName = displayName.removeSuffix(".zip").removeSuffix(".ZIP").trim()
        if (packName.isEmpty()) return Result.Failure("文件名无效")
        if (packName.contains("/") || packName.contains("\\")) {
            return Result.Failure("文件名包含非法字符")
        }

        // ② 检查冲突
        if (packExists(context, packName)) {
            return Result.Failure("资源包「$packName」已存在，请重命名后再导入")
        }

        // ③ 准备目标目录
        val rootDir = File(context.getExternalFilesDir(null), EXTERNAL_ROOT)
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            return Result.Failure("无法创建资源包目录")
        }
        val targetDir = File(rootDir, packName)

        // ④ 第一遍扫描：判断 zip 是否有公共顶层文件夹
        val entryNames = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) entryNames.add(entry.name)
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return Result.Failure("无法打开 zip")
        } catch (e: Exception) {
            return Result.Failure("扫描 zip 失败: ${e.message}")
        }
        if (entryNames.isEmpty()) return Result.Failure("zip 是空的")

        val commonPrefix = findCommonDirPrefix(entryNames)
        Log.i(TAG, "zip 内共 ${entryNames.size} 个文件，公共前缀 = 「$commonPrefix」")

        // ⑤ 第二遍解压
        var fileCount = 0
        try {
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return Result.Failure("无法创建目标目录")
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val rawName = entry.name
                        if (!entry.isDirectory && rawName.startsWith(commonPrefix)) {
                            val relName = rawName.substring(commonPrefix.length)
                            if (relName.isNotEmpty()) {
                                val outFile = File(targetDir, relName)
                                // Zip Slip 防护
                                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)) {
                                    Log.w(TAG, "跳过可疑条目: $rawName")
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    outFile.outputStream().use { out -> zis.copyTo(out) }
                                    fileCount++
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: run {
                targetDir.deleteRecursively()
                return Result.Failure("解压时无法打开 zip")
            }
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            return Result.Failure("解压失败: ${e.message}")
        }

        // ⑥ 必须有 info.yml
        val infoFile = File(targetDir, "info.yml")
        if (!infoFile.exists()) {
            targetDir.deleteRecursively()
            return Result.Failure("资源包缺少 info.yml")
        }

        Log.i(TAG, "✅ 导入成功：「$packName」，$fileCount 个文件 → ${targetDir.absolutePath}")
        // 顺手把解压出来的东西列个日志
        targetDir.listFiles()?.forEach { f ->
            Log.i(TAG, "   - ${f.name} (${if (f.isDirectory) "dir" else "${f.length()}B"})")
        }

        return Result.Success(packName, fileCount, targetDir)
    }

    /**
     * 删除一个外部导入的资源包。只删 getExternalFilesDir 里的，
     * 绝不碰 assets。
     * @return true 表示删除成功（或本来就不存在）
     */
    fun deleteImportedPack(context: Context, packName: String): Boolean {
        val targetDir = File(
            context.getExternalFilesDir(null),
            "$EXTERNAL_ROOT/$packName"
        )
        if (!targetDir.exists()) return true
        // 保险：确保路径真的在 external root 下，防止 packName 里带 ../ 之类
        val rootDir = File(context.getExternalFilesDir(null), EXTERNAL_ROOT)
        if (!targetDir.canonicalPath.startsWith(rootDir.canonicalPath + File.separator)) {
            Log.w(TAG, "拒绝删除非法路径: ${targetDir.absolutePath}")
            return false
        }
        val ok = targetDir.deleteRecursively()
        if (ok) Log.i(TAG, "已删除资源包: $packName")
        else Log.w(TAG, "删除资源包失败: $packName")
        return ok
    }

    /** 判断所有文件是否都在同一个顶层目录下，是则返回 "xxx/"，否则返回 "" */
    private fun findCommonDirPrefix(names: List<String>): String {
        val firstSlash = names[0].indexOf('/')
        if (firstSlash < 0) return ""
        val candidate = names[0].substring(0, firstSlash + 1)
        return if (names.all { it.startsWith(candidate) }) candidate else ""
    }

    /** 检查外部目录或 assets 里是否已存在同名包 */
    private fun packExists(context: Context, packName: String): Boolean {
        val extDir = File(context.getExternalFilesDir(null), "$EXTERNAL_ROOT/$packName")
        if (extDir.exists()) return true
        return try {
            context.assets.list(ASSETS_ROOT)?.contains(packName) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) return c.getString(idx)
            }
        }
        return null
    }
}