package com.moocengg.harmonica_indicator

import kotlin.math.roundToInt

/**
 * 设置码编解码器。
 * 每个值归一化成 0..63 的整数，用 base64 字符集里的一个字符表示。
 * 版本位：高级用 'A'，基础用 'B'。
 */
object SettingsCodec {

    private const val VERSION_ADV = 'A'
    private const val VERSION_BAS = 'B'
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    // ============ 高级设置（10 项）============

    fun encodeAdvanced(): String {
        val sb = StringBuilder()
        sb.append(VERSION_ADV)
        sb.append(enc(SettingsManager.circleAlphaMin / 5))                        // 0..51
        sb.append(enc(SettingsManager.circleAlphaMax / 5))                        // 0..51
        sb.append(enc((SettingsManager.funcLineSpeed * 20).roundToInt() - 10))    // 0..30
        sb.append(enc((SettingsManager.noteLineSpeed * 20).roundToInt() - 10))    // 0..30
        sb.append(enc(SettingsManager.funcLineType - 1))                          // 0..1
        sb.append(enc(SettingsManager.noteLineType - 1))                          // 0..1
        sb.append(enc(SettingsManager.funcLineNoteType - 1))                      // 0..3
        sb.append(enc(if (SettingsManager.funcLineReverse) 1 else 0))             // 0..1
        sb.append(enc(SettingsManager.noteLineNoteType - 1))                      // 0..3
        sb.append(enc(if (SettingsManager.noteLineReverse) 1 else 0))             // 0..1
        return sb.toString()
    }

    fun decodeAdvanced(code: String): DecodeResult {
        if (code.isEmpty() || code[0] != VERSION_ADV)
            return DecodeResult.Failure("不是高级设置码（应以 A 开头）")
        val body = code.substring(1)
        if (body.length < 10) return DecodeResult.Failure("设置码格式错误")
        return try {
            var p = 0
            fun next() = dec(body[p++])
            SettingsManager.circleAlphaMin = next() * 5
            SettingsManager.circleAlphaMax = next() * 5
            SettingsManager.funcLineSpeed = (next() + 10) / 20f
            SettingsManager.noteLineSpeed = (next() + 10) / 20f
            SettingsManager.funcLineType = next() + 1
            SettingsManager.noteLineType = next() + 1
            SettingsManager.funcLineNoteType = next() + 1
            SettingsManager.funcLineReverse = next() == 1
            SettingsManager.noteLineNoteType = next() + 1
            SettingsManager.noteLineReverse = next() == 1
            DecodeResult.Success(10)
        } catch (e: Exception) {
            DecodeResult.Failure("解析失败: ${e.message}")
        }
    }

    // ============ 基础设置（5 项）============

    fun encodeBasic(): String {
        val sb = StringBuilder()
        sb.append(VERSION_BAS)
        // fallVelo: 400~2000，步长 25 → 档位 0~64，压到 0~63（2000 会折成 1975，无所谓）
        sb.append(enc(((SettingsManager.fallVelo - 400) / 25).coerceIn(0, 63)))
        sb.append(enc((SettingsManager.noteSizeBonus * 20).roundToInt() - 10))    // 0..30
        sb.append(enc(SettingsManager.circleShrinkRate / 25 - 4))                 // 0..36
        sb.append(enc((SettingsManager.circleLeadTime * 20).roundToInt()))        // 0..50
        sb.append(enc((SettingsManager.effectSizeBonus * 20).roundToInt() - 10))  // 0..30
        return sb.toString()
    }

    fun decodeBasic(code: String): DecodeResult {
        if (code.isEmpty() || code[0] != VERSION_BAS)
            return DecodeResult.Failure("不是基础设置码（应以 B 开头）")
        val body = code.substring(1)
        if (body.length < 5) return DecodeResult.Failure("设置码格式错误")
        return try {
            var p = 0
            fun next() = dec(body[p++])
            SettingsManager.fallVelo = next() * 25 + 400
            SettingsManager.noteSizeBonus = (next() + 10) / 20f
            SettingsManager.circleShrinkRate = (next() + 4) * 25
            SettingsManager.circleLeadTime = next() / 20f
            SettingsManager.effectSizeBonus = (next() + 10) / 20f
            DecodeResult.Success(5)
        } catch (e: Exception) {
            DecodeResult.Failure("解析失败: ${e.message}")
        }
    }

    // ============ 内部工具 ============

    private fun enc(v: Int): Char = ALPHABET[v.coerceIn(0, 63)]
    private fun dec(c: Char): Int {
        val i = ALPHABET.indexOf(c)
        if (i < 0) throw IllegalArgumentException("非法字符 '$c'")
        return i
    }

    sealed class DecodeResult {
        data class Success(val fieldCount: Int) : DecodeResult()
        data class Failure(val reason: String) : DecodeResult()
    }
}