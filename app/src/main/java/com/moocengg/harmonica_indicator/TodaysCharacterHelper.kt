package com.moocengg.harmonica_indicator

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

object TodaysCharacterHelper {

    private const val SP_NAME = "harmonica_settings"
    private const val KEY_DEVICE_UUID = "device_uuid"

    /** 取本机唯一码，第一次用时生成并落盘。放在 SettingsManager 用的同一个 SP 里。 */
    private fun getDeviceUuid(context: Context): String {
        val sp = context.applicationContext
            .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        var uuid = sp.getString(KEY_DEVICE_UUID, null)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            sp.edit().putString(KEY_DEVICE_UUID, uuid).apply()
        }
        return uuid
    }

    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** 今日人品值 0..100，同一天同一设备结果恒定 */
    fun getTodayValue(context: Context): Int {
        val uuid = getDeviceUuid(context)
        val date = todayDateString()
        // 稳定种子：UUID.hashCode × 大质数 + 日期.hashCode
        val seed = uuid.hashCode().toLong() * 1_000_003L + date.hashCode().toLong()
        return Random(seed).nextInt(0, 101)
    }

    /** 根据人品值返回文案，你随便改 */
    fun getComment(value: Int): String = when (value) {
        0 -> "……这……某种意义上，你也是欧皇……吧（？）"
        in 1..5 -> "今日宜躺平，别碰抽卡……别碰抽卡……"
        in 6..15 -> "现在对着手机喊一声水逆退散提高明日人品（确信）"
        in 16..30 -> "运气差那么一丢丢，但问题不大~"
        in 31..50 -> "普普通通，没什么特别值得高兴的……\n不过也没什么特别值得悲伤的~\n不是嘛？（笑）"
        in 51..70 -> "还不错，去做点想做的事！"
        in 71..85 -> "不错的一天~运气在你这边！"
        in 86..95 -> "今天的你，如有神助~"
        in 96..99 -> "欧皇降临！去买张彩票吧~"
        100 -> "百天难遇！今天你就是天选之人！\n【开发者脚注】\n这么好运？分我点呗，朋友~"
        else -> ""
    }
}