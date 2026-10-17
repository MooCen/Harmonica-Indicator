package com.moocengg.harmonica_indicator

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/*import androidx.appcompat.app.AppCompatDelegate*/

/**
 * 全局设置管理器，类似 Unity 的 PlayerPrefs。
 * 所有 key 集中在这里，避免各处魔法字符串写错。
 */
object SettingsManager {

    private const val SP_NAME = "harmonica_settings"

    // ====== key 统一在这里定义 ======
    private const val KEY_LAST_CALIBRATION_POS = "last_calibration_pos"
    private const val KEY_LAST_CALIBRATION_SCALE = "last_calibration_scale"


    private const val KEY_THEME_MODE = "theme_mode"
    //private const val KEY_PRIMARY_COLOR = "primary_color"
    private const val KEY_PLAY_SPEED = "play_speed"
    private const val KEY_RESOURCE_PACK = "resource_pack"
    private const val KEY_BACKGROUND_ALPHA = "background_alpha"
    private const val KEY_FALL_VELO = "fall_velo"
    private const val KEY_NOTE_SIZE_BONUS = "note_size_bonus"
    private const val KEY_CIRCLE_SHRINK_RATE = "circle_shrink_rate"
    private const val KEY_CIRCLE_LEAD_TIME = "circle_lead_time"
    private const val KEY_EFFECT_SIZE_BONUS = "effect_size_bonus"
    private const val KEY_ALPHA_RANGE = "circle_alpha_range"
    private const val KEY_HIDE_BASIC_SETTING = "hide_basic_setting"
    private const val KEY_FUNC_LINE_SPEED = "func_line_speed"
    private const val KEY_NOTE_LINE_SPEED = "note_line_speed"
    private const val KEY_FUNC_LINE_TYPE = "func_line_type"
    private const val KEY_NOTE_LINE_TYPE = "note_line_type"
    private const val KEY_FUNCLINE_NOTE_TYPE = "funcline_note_type"
    private const val KEY_FUNC_LINE_REVERSE = "func_line_reverse"
    private const val KEY_NOTELINE_NOTE_TYPE = "noteline_note_type"
    private const val KEY_NOTE_LINE_REVERSE = "note_line_reverse"

    private lateinit var sp: SharedPreferences

    /** 在 Application.onCreate 或 MainActivity.onCreate 里调一次即可。 */
    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        }
    }

    // ====== 属性写法，读的时候像字段一样 ======
    var lastCalibrationPosX: Float
        get() = sp.getFloat(KEY_LAST_CALIBRATION_POS + "_X", 0f)
        set(v) = sp.edit().putFloat(KEY_LAST_CALIBRATION_POS + "_X", v).apply()
    var lastCalibrationPosY: Float
        get() = sp.getFloat(KEY_LAST_CALIBRATION_POS + "_Y", 0f)
        set(v) = sp.edit().putFloat(KEY_LAST_CALIBRATION_POS + "_Y",v).apply()

    var lastCalibrationScaleX: Float
        get() = sp.getFloat(KEY_LAST_CALIBRATION_SCALE + "_X", -1f)
        set(v) = sp.edit().putFloat(KEY_LAST_CALIBRATION_SCALE + "_X",v).apply()
    var lastCalibrationScaleY: Float
        get() = sp.getFloat(KEY_LAST_CALIBRATION_SCALE + "_Y", -1f)
        set(v) = sp.edit().putFloat(KEY_LAST_CALIBRATION_SCALE + "_Y",v).apply()

    var themeMode: Int
        get() = sp.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(v) = sp.edit().putInt(KEY_THEME_MODE, v).apply()

    /*var primaryColor: Int
        get() = sp.getInt(KEY_PRIMARY_COLOR, 0xFFF5A3D0.toInt())
        set(v) = sp.edit().putInt(KEY_PRIMARY_COLOR, v).apply()
        */
    var playSpeed: Float
        get() = sp.getFloat(KEY_PLAY_SPEED, 1f)
        set(v) = sp.edit().putFloat(KEY_PLAY_SPEED, v).apply()

    var resourcePack: String
        get() = sp.getString(KEY_RESOURCE_PACK, "resource_phigros_replicate") ?: "resource_phigros_replicate"
        set(v) = sp.edit().putString(KEY_RESOURCE_PACK, v).apply()

    var backgroundAlpha: Int
        get() = sp.getInt(KEY_BACKGROUND_ALPHA, 150)
        set(v) = sp.edit().putInt(KEY_BACKGROUND_ALPHA, v).apply()

    var fallVelo: Int
        get() = sp.getInt(KEY_FALL_VELO, 900)
        set(v) = sp.edit().putInt(KEY_FALL_VELO, v).apply()

    var noteSizeBonus: Float
        get() = sp.getFloat(KEY_NOTE_SIZE_BONUS, 1f)
        set(v) = sp.edit().putFloat(KEY_NOTE_SIZE_BONUS, v).apply()

    var circleShrinkRate: Int
        get() = sp.getInt(KEY_CIRCLE_SHRINK_RATE, 400)
        set(v) = sp.edit().putInt(KEY_CIRCLE_SHRINK_RATE, v).apply()

    var circleLeadTime: Float
        get() = sp.getFloat(KEY_CIRCLE_LEAD_TIME, 0.75f)
        set(v) = sp.edit().putFloat(KEY_CIRCLE_LEAD_TIME, v).apply()

    var effectSizeBonus: Float
        get() = sp.getFloat(KEY_EFFECT_SIZE_BONUS, 1f)
        set(v) = sp.edit().putFloat(KEY_EFFECT_SIZE_BONUS, v).apply()

    var hideBasicSetting: Boolean
        get() = sp.getBoolean(KEY_HIDE_BASIC_SETTING, false)
        set(v) = sp.edit().putBoolean(KEY_HIDE_BASIC_SETTING, v).apply()

    var circleAlphaMin: Int
        get() = sp.getInt(KEY_ALPHA_RANGE + "_Min", 40)
        set(v) = sp.edit().putInt(KEY_ALPHA_RANGE + "_Min", v).apply()

    var circleAlphaMax: Int
        get() = sp.getInt(KEY_ALPHA_RANGE + "_Max", 255)
        set(v) = sp.edit().putInt(KEY_ALPHA_RANGE + "_Max", v).apply()

    var funcLineSpeed: Float
        get() = sp.getFloat(KEY_FUNC_LINE_SPEED, 1f)
        set(v) = sp.edit().putFloat(KEY_FUNC_LINE_SPEED, v).apply()

    var noteLineSpeed: Float
        get() = sp.getFloat(KEY_NOTE_LINE_SPEED, 1f)
        set(v) = sp.edit().putFloat(KEY_NOTE_LINE_SPEED, v).apply()

    var funcLineType: Int
        get() = sp.getInt(KEY_FUNC_LINE_TYPE, 2)
        set(v) = sp.edit().putInt(KEY_FUNC_LINE_TYPE, v).apply()

    var noteLineType: Int
        get() = sp.getInt(KEY_NOTE_LINE_TYPE, 1)
        set(v) = sp.edit().putInt(KEY_NOTE_LINE_TYPE, v).apply()

    var funcLineNoteType: Int
        get() = sp.getInt(KEY_FUNCLINE_NOTE_TYPE, 3)
        set(v) = sp.edit().putInt(KEY_FUNCLINE_NOTE_TYPE, v).apply()

    var funcLineReverse: Boolean
        get() = sp.getBoolean(KEY_FUNC_LINE_REVERSE, false)
        set(v) = sp.edit().putBoolean(KEY_FUNC_LINE_REVERSE, v).apply()

    var noteLineNoteType: Int
        get() = sp.getInt(KEY_NOTELINE_NOTE_TYPE, 1)
        set(v) = sp.edit().putInt(KEY_NOTELINE_NOTE_TYPE, v).apply()

    var noteLineReverse: Boolean
        get() = sp.getBoolean(KEY_NOTE_LINE_REVERSE, false)
        set(v) = sp.edit().putBoolean(KEY_NOTE_LINE_REVERSE, v).apply()
}