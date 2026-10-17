package com.moocengg.harmonica_indicator


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AdvancedSettingActivity : AppCompatActivity() {

    private lateinit var rootView: LinearLayout
    private lateinit var hideBasicSetting_checkbox: CheckBox
    private lateinit var circleAlphaMin_seekbar: SeekBar
    private lateinit var circleAlphaMin_text: TextView
    private lateinit var circleAlphaMax_seekbar: SeekBar
    private lateinit var circleAlphaMax_text: TextView
    private lateinit var funcLineSpeed_seekbar: SeekBar
    private lateinit var funcLineSpeed_text: TextView
    private lateinit var noteLineSpeed_seekbar: SeekBar
    private lateinit var noteLineSpeed_text: TextView
    val syllableTypes = listOf("音符", "指示圈")
    private lateinit var funcLineType_spinner: Spinner
    private lateinit var noteLineType_spinner: Spinner
    val noteTypes = listOf("Tap", "Drag", "Flick", "Hold(极短版)")
    private lateinit var funcLineNoteTypeCollection: LinearLayout
    private lateinit var funcLineNoteType_spinner: Spinner
    private lateinit var funcLineReverse_switchcompat: SwitchCompat
    private lateinit var noteLineNoteTypeCollection: LinearLayout
    private lateinit var noteLineNoteType_spinner: Spinner
    private lateinit var noteLineReverse_switchcompat: SwitchCompat
    private lateinit var warn_hint: TextView

    // 基础设置
    private lateinit var fallVelo_seekbar: SeekBar
    private lateinit var fallVelo_text: TextView
    private lateinit var noteSizeBonus_seekbar: SeekBar
    private lateinit var noteSizeBonus_text: TextView
    private lateinit var circleShrinkRate_seekbar: SeekBar
    private lateinit var circleShrinkRate_text: TextView
    private lateinit var circleLeadTime_seekbar: SeekBar
    private lateinit var circleLeadTime_text: TextView
    private lateinit var effectSizeBonus_seekbar: SeekBar
    private lateinit var effectSizeBonus_text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_advanced_setting)
        rootView = findViewById<LinearLayout>(R.id.advanced_settings_root)
        findViewById<View>(R.id.advanced_setting_moreSetting_codeBtn).setOnClickListener {
            showCodeDialog(isAdvanced = true)
        }
        findViewById<View>(R.id.advanced_setting_basicSetting_codeBtn).setOnClickListener {
            showCodeDialog(isAdvanced = false)
        }
        hideBasicSetting_checkbox = findViewById(R.id.advanced_setting_hideBasicSetting_checkbox)
        circleAlphaMin_seekbar = findViewById(R.id.advanced_setting_circleAlphaMin_seekbar)
        circleAlphaMin_text = findViewById(R.id.advanced_setting_circleAlphaMin_rate)
        circleAlphaMax_seekbar = findViewById(R.id.advanced_setting_circleAlphaMax_seekbar)
        circleAlphaMax_text = findViewById(R.id.advanced_setting_circleAlphaMax_rate)
        funcLineSpeed_seekbar = findViewById(R.id.advanced_setting_funcLineSpeed_seekbar)
        funcLineSpeed_text = findViewById(R.id.advanced_setting_funcLineSpeed_rate)
        noteLineSpeed_seekbar = findViewById(R.id.advanced_setting_noteLineSpeed_seekbar)
        noteLineSpeed_text = findViewById(R.id.advanced_setting_noteLineSpeed_rate)
        funcLineType_spinner = findViewById(R.id.advanced_setting_funcLineType_spinner)
        noteLineType_spinner = findViewById(R.id.advanced_setting_noteLineType_spinner)
        funcLineNoteTypeCollection = findViewById(R.id.advanced_setting_funcLineNoteType)
        funcLineNoteType_spinner = findViewById(R.id.advanced_setting_funcLineNoteType_spinner)
        funcLineReverse_switchcompat = findViewById(R.id.advanced_setting_funcLineReverse_switchcompat)
        noteLineNoteTypeCollection = findViewById(R.id.advanced_setting_noteLineNoteType)
        noteLineNoteType_spinner = findViewById(R.id.advanced_setting_noteLineNoteType_spinner)
        noteLineReverse_switchcompat = findViewById(R.id.advanced_setting_noteLineReverse_switchcompat)
        warn_hint = findViewById(R.id.advanced_setting_warnHint)

        // 基础设置
        fallVelo_seekbar = findViewById(R.id.advanced_setting_fallVelo_seekbar)
        fallVelo_text = findViewById(R.id.advanced_setting_fallVelo_rate)
        noteSizeBonus_seekbar = findViewById(R.id.advanced_setting_noteSizeBonus_seekbar)
        noteSizeBonus_text = findViewById(R.id.advanced_setting_noteSizeBonus_rate)
        circleShrinkRate_seekbar = findViewById(R.id.advanced_setting_circleShrinkRate_seekbar)
        circleShrinkRate_text = findViewById(R.id.advanced_setting_circleShrinkRate_rate)
        circleLeadTime_seekbar = findViewById(R.id.advanced_setting_circleLeadTime_seekbar)
        circleLeadTime_text = findViewById(R.id.advanced_setting_circleLeadTime_rate)
        effectSizeBonus_seekbar = findViewById(R.id.advanced_setting_effectSizeBonus_seekbar)
        effectSizeBonus_text = findViewById(R.id.advanced_setting_effectSizeBonus_rate)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.advanced_settings_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.advanced_settings_Toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // 显示返回箭头
        supportActionBar?.title = "指示器高级设置"

        // 返回箭头点击 → 关闭本页
        toolbar.setNavigationOnClickListener { finish() }

        // 高级设置

        hideBasicSetting_checkbox.isChecked = SettingsManager.hideBasicSetting
        findViewById<LinearLayout>(R.id.advanced_setting_basic_setting_collection).visibility =
            if(hideBasicSetting_checkbox.isChecked) View.GONE
            else View.VISIBLE

        hideBasicSetting_checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
            val transition = AutoTransition().apply {
                duration = 200
            }
            TransitionManager.beginDelayedTransition(rootView, transition)

            findViewById<LinearLayout>(R.id.advanced_setting_basic_setting_collection).visibility =
                if(isChecked) View.GONE
                else View.VISIBLE
            SettingsManager.hideBasicSetting = isChecked
        }

        circleAlphaMin_seekbar.max = SettingsManager.circleAlphaMax / 5
        circleAlphaMin_seekbar.progress = SettingsManager.circleAlphaMin / 5
        circleAlphaMin_text.text = "${SettingsManager.circleAlphaMin} / ${SettingsManager.circleAlphaMax}"
        circleAlphaMin_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.circleAlphaMin = progress * 5
                    if(circleAlphaMax_seekbar.progress < progress){
                        circleAlphaMax_seekbar.progress = progress
                    }
                    circleAlphaMin_text.text = "${progress * 5} / ${SettingsManager.circleAlphaMax}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        //circleAlphaMax_seekbar.min = SettingsManager.circleAlphaMin / 5
        circleAlphaMax_seekbar.progress = SettingsManager.circleAlphaMax / 5
        circleAlphaMax_text.text = "${SettingsManager.circleAlphaMax} / 255"
        circleAlphaMax_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.circleAlphaMax = progress * 5
                    circleAlphaMax_text.text = "${progress * 5} / 255"

                    if(circleAlphaMin_seekbar.progress > progress){
                        circleAlphaMin_seekbar.progress = progress
                        SettingsManager.circleAlphaMin = progress * 5
                    }
                    circleAlphaMin_seekbar.max = progress
                    circleAlphaMin_text.text = "${SettingsManager.circleAlphaMin} / ${progress * 5}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        funcLineSpeed_seekbar.progress = (SettingsManager.funcLineSpeed * 20).toInt()
        funcLineSpeed_text.text = " x${"%.2f".format(SettingsManager.funcLineSpeed)}"
        funcLineSpeed_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.funcLineSpeed = progress / 20f
                    funcLineSpeed_text.text = " x${"%.2f".format(progress / 20f)}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        noteLineSpeed_seekbar.progress = (SettingsManager.noteLineSpeed * 20).toInt()
        noteLineSpeed_text.text = " x${"%.2f".format(SettingsManager.noteLineSpeed)}"
        noteLineSpeed_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.noteLineSpeed = progress / 20f
                    noteLineSpeed_text.text = " x${"%.2f".format(progress / 20f)}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        funcLineType_spinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, syllableTypes)
        funcLineReverse_switchcompat.isEnabled = (SettingsManager.funcLineType == 1)
        // 进来时选中当前的
        val curFuncLineTypeIdx = SettingsManager.funcLineType - 1 // 1是音符 2 是指示圈，经典0开始所以减一，以后犯混的时候看这里别觉得有问题动了
        funcLineType_spinner.setSelection(curFuncLineTypeIdx)
        // 选中时保存
        funcLineType_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.funcLineType = position + 1 // 同样
                funcLineReverse_switchcompat.isEnabled = (SettingsManager.funcLineType == 1)
                checkIfSettingMayConflict()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


        noteLineType_spinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, syllableTypes)
        noteLineReverse_switchcompat.isEnabled = (SettingsManager.noteLineType == 1)
        // 进来时选中当前的
        val curNoteLineTypeIdx = SettingsManager.noteLineType - 1 // 1是音符 2 是指示圈，经典0开始所以减一，以后犯混的时候看这里别觉得有问题动了
        noteLineType_spinner.setSelection(curNoteLineTypeIdx)

        // 选中时保存
        noteLineType_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.noteLineType = position + 1 // 同样
                noteLineReverse_switchcompat.isEnabled = (SettingsManager.noteLineType == 1)
                checkIfSettingMayConflict()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        funcLineNoteType_spinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, noteTypes)
        // 进来时选中当前的
        val curFuncLineNoteTypeIdx = SettingsManager.funcLineNoteType - 1
        funcLineNoteType_spinner.setSelection(curFuncLineNoteTypeIdx)
        // 选中时保存
        funcLineNoteType_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.funcLineNoteType = position + 1
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        funcLineReverse_switchcompat.isChecked = SettingsManager.funcLineReverse
        funcLineReverse_switchcompat.setOnCheckedChangeListener { button, isChecked ->
            SettingsManager.funcLineReverse = isChecked
            checkIfSettingMayConflict()
        }

        noteLineNoteType_spinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, noteTypes)
        // 进来时选中当前的
        val curNoteLineNoteTypeIdx = SettingsManager.noteLineNoteType - 1
        noteLineNoteType_spinner.setSelection(curNoteLineNoteTypeIdx)
        // 选中时保存
        noteLineNoteType_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.noteLineNoteType = position + 1
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        noteLineReverse_switchcompat.isChecked = SettingsManager.noteLineReverse
        noteLineReverse_switchcompat.setOnCheckedChangeListener { button, isChecked ->
            SettingsManager.noteLineReverse = isChecked
            checkIfSettingMayConflict()
        }

        // 基础设置

        // 初始化时把当前值填进去
        fallVelo_seekbar.max = 80
        fallVelo_seekbar.progress = (SettingsManager.fallVelo) / 25
        fallVelo_text.text = "${SettingsManager.fallVelo}px/s"

        // 拖动时保存
        fallVelo_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.fallVelo = progress * 25
                    fallVelo_text.text = "${progress * 25}px/s"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })


        // 初始化时把当前值填进去
        noteSizeBonus_seekbar.max = 40
        noteSizeBonus_seekbar.progress = (SettingsManager.noteSizeBonus * 20).toInt()
        noteSizeBonus_text.text = " x${"%.2f".format(SettingsManager.noteSizeBonus)}"

        // 拖动时保存
        noteSizeBonus_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.noteSizeBonus = progress / 20f
                    noteSizeBonus_text.text = " x${"%.2f".format(progress / 20f)}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 初始化时把当前值填进去
        //circleShrinkRate_seekbar.max = 40
        circleShrinkRate_seekbar.progress = (SettingsManager.circleShrinkRate) / 25
        circleShrinkRate_text.text = "${SettingsManager.circleShrinkRate}px/s"

        // 拖动时保存
        circleShrinkRate_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.circleShrinkRate = progress * 25
                    circleShrinkRate_text.text = "${progress * 25}px/s"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })



        // 初始化时把当前值填进去
        //circleLeadTime_seekbar.max = 40
        circleLeadTime_seekbar.progress = (SettingsManager.circleLeadTime * 20).toInt()
        circleLeadTime_text.text = "${"%.2f".format(SettingsManager.circleLeadTime)}s"

        // 拖动时保存
        circleLeadTime_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.circleLeadTime = progress / 20f
                    circleLeadTime_text.text = "${"%.2f".format(progress / 20f)}s"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })


        // 初始化时把当前值填进去
        effectSizeBonus_seekbar.max = 40
        effectSizeBonus_seekbar.progress = (SettingsManager.effectSizeBonus * 20).toInt()
        effectSizeBonus_text.text = " x${"%.2f".format(SettingsManager.effectSizeBonus)}"

        // 拖动时保存
        effectSizeBonus_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.effectSizeBonus = progress / 20f
                    effectSizeBonus_text.text = " x${"%.2f".format(progress / 20f)}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val btnResetAll = findViewById<Button>(R.id.advanced_setting_resetAllBasic_button)

        btnResetAll.setOnClickListener {
            resetAllBasicSetting()
        }

        val btnResetAllAdvance = findViewById<Button>(R.id.advanced_setting_resetAllAdvanced_button)

        btnResetAllAdvance.setOnClickListener {
            resetAllAdvancedSetting()
        }
    }
    private fun checkIfSettingMayConflict(){
        val transition = AutoTransition().apply {
            duration = 300
        }
        TransitionManager.beginDelayedTransition(rootView, transition)
        funcLineNoteTypeCollection.visibility = if(SettingsManager.funcLineType == 1) View.VISIBLE else View.GONE
        noteLineNoteTypeCollection.visibility = if(SettingsManager.noteLineType == 1) View.VISIBLE else View.GONE

        if((SettingsManager.funcLineType == SettingsManager.noteLineType && (SettingsManager.noteLineType == 1)) // 均为“音符”
            && (SettingsManager.funcLineReverse == SettingsManager.noteLineReverse) // 同向
            ){
            warn_hint.text = "同向下落音符可能致使读谱困难！"
            warn_hint.visibility = View.VISIBLE
            return
        }
        warn_hint.visibility = View.GONE
        return
    }

    private fun showCodeDialog(isAdvanced: Boolean) {
        val currentCode = if (isAdvanced) SettingsCodec.encodeAdvanced()
        else SettingsCodec.encodeBasic()
        val title = if (isAdvanced) "高级设置码" else "基础设置码"
        val hintLen = if (isAdvanced) "共 ${currentCode.length} 位" else "共 ${currentCode.length} 位"

        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()

        // 显示当前码
        val tvCurrent = TextView(this).apply {
            text = currentCode
            textSize = 20f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(pad, pad, pad, pad)
        }

        // 输入框（粘贴别人的码）
        val etInput = EditText(this).apply {
            hint = "在此粘贴设置码"
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setSingleLine()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(TextView(this@AdvancedSettingActivity).apply {
                text = "本机当前设置码（$hintLen）："
                textSize = 14f
            })
            addView(tvCurrent)
            addView(TextView(this@AdvancedSettingActivity).apply {
                text = "\n从其它设备导入："
                textSize = 14f
                setPadding(0, pad / 2, 0, 0)
            })
            addView(etInput)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("导入") { _, _ ->
                val code = etInput.text.toString().trim()
                if (code.isEmpty()) {
                    Toast.makeText(this, "请输入设置码", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val result = if (isAdvanced) SettingsCodec.decodeAdvanced(code)
                else SettingsCodec.decodeBasic(code)
                when (result) {
                    is SettingsCodec.DecodeResult.Success -> {
                        Toast.makeText(
                            this,
                            "导入成功，${result.fieldCount} 项设置已更新",
                            Toast.LENGTH_LONG
                        ).show()
                        refreshBasicSettingUI()
                        refreshAdvancedSettingUI()
                    }
                    is SettingsCodec.DecodeResult.Failure -> {
                        Toast.makeText(
                            this,
                            "导入失败：${result.reason}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNeutralButton("复制") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("设置码", currentCode))
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun resetAllAdvancedSetting(){
        SettingsManager.circleAlphaMin = 40
        SettingsManager.circleAlphaMax = 255
        SettingsManager.funcLineSpeed = 1f
        SettingsManager.noteLineSpeed = 1f
        SettingsManager.funcLineType = 2
        SettingsManager.noteLineType = 1
        SettingsManager.funcLineNoteType = 3
        SettingsManager.funcLineReverse = false
        SettingsManager.noteLineNoteType = 1
        SettingsManager.noteLineReverse = false
        refreshAdvancedSettingUI()
    }
    private fun refreshAdvancedSettingUI(){
        circleAlphaMin_seekbar.max = SettingsManager.circleAlphaMax / 5
        circleAlphaMin_seekbar.progress = SettingsManager.circleAlphaMin / 5
        circleAlphaMin_text.text = "${SettingsManager.circleAlphaMin} / ${SettingsManager.circleAlphaMax}"
        circleAlphaMin_seekbar.max = SettingsManager.circleAlphaMax / 5
        circleAlphaMin_seekbar.progress = SettingsManager.circleAlphaMin / 5
        circleAlphaMin_text.text = "${SettingsManager.circleAlphaMin} / ${SettingsManager.circleAlphaMax}"
        funcLineSpeed_seekbar.progress = (SettingsManager.funcLineSpeed * 20).toInt()
        funcLineSpeed_text.text = " x${"%.2f".format(SettingsManager.funcLineSpeed)}"
        noteLineSpeed_seekbar.progress = (SettingsManager.noteLineSpeed * 20).toInt()
        noteLineSpeed_text.text = " x${"%.2f".format(SettingsManager.noteLineSpeed)}"
        val curFuncLineTypeIdx = SettingsManager.funcLineType - 1 // 1是音符 2 是指示圈，经典0开始所以减一，以后犯混的时候看这里别觉得有问题动了
        funcLineType_spinner.setSelection(curFuncLineTypeIdx)
        val curNoteLineTypeIdx = SettingsManager.noteLineType - 1 // 1是音符 2 是指示圈，经典0开始所以减一，以后犯混的时候看这里别觉得有问题动了
        noteLineType_spinner.setSelection(curNoteLineTypeIdx)
        val curFuncLineNoteTypeIdx = SettingsManager.funcLineNoteType - 1
        funcLineNoteType_spinner.setSelection(curFuncLineNoteTypeIdx)
        funcLineReverse_switchcompat.isChecked = SettingsManager.funcLineReverse
        val curNoteLineNoteTypeIdx = SettingsManager.noteLineNoteType - 1
        noteLineNoteType_spinner.setSelection(curNoteLineNoteTypeIdx)
        noteLineReverse_switchcompat.isChecked = SettingsManager.noteLineReverse
        checkIfSettingMayConflict()
    }
    private fun resetAllBasicSetting(){
        SettingsManager.fallVelo = 900
        SettingsManager.noteSizeBonus = 1f
        SettingsManager.circleShrinkRate = 400
        SettingsManager.circleLeadTime = 0.75f
        SettingsManager.effectSizeBonus = 1f
        refreshBasicSettingUI()
    }
    private fun refreshBasicSettingUI(){
        fallVelo_seekbar.progress = (SettingsManager.fallVelo) / 25
        fallVelo_text.text = "${SettingsManager.fallVelo}px/s"
        noteSizeBonus_seekbar.progress = (SettingsManager.noteSizeBonus * 20).toInt()
        noteSizeBonus_text.text = " x${"%.2f".format(SettingsManager.noteSizeBonus)}"
        circleShrinkRate_seekbar.progress = (SettingsManager.circleShrinkRate) / 25
        circleShrinkRate_text.text = "${SettingsManager.circleShrinkRate}px/s"
        circleLeadTime_seekbar.progress = (SettingsManager.circleLeadTime * 20).toInt()
        circleLeadTime_text.text = "${"%.2f".format(SettingsManager.circleLeadTime)}s"
        effectSizeBonus_seekbar.progress = (SettingsManager.effectSizeBonus * 20).toInt()
        effectSizeBonus_text.text = " x${"%.2f".format(SettingsManager.effectSizeBonus)}"
    }
}