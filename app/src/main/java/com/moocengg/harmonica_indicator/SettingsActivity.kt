package com.moocengg.harmonica_indicator

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    /*val rgTheme = findViewById<RadioGroup>(R.id.setting_theme)
    val backgroundAlpha_seekbar = findViewById<SeekBar>(R.id.setting_backgroundAlpha_seekbar)
    val backgroundAlpha_text = findViewById<TextView>(R.id.setting_backgroundAlpha_rate)
    val packNames = listOf("resource_phigros_replicate", "resource_sakura")
    val spinner = findViewById<Spinner>(R.id.setting_resourcePack_spinner)
    val playSpeed_seekbar = findViewById<SeekBar>(R.id.setting_playSpeed_seekbar)
    val playSpeed_text = findViewById<TextView>(R.id.setting_playSpeed_rate)
    val fallVelo_seekbar = findViewById<SeekBar>(R.id.setting_fallVelo_seekbar)
    val fallVelo_text = findViewById<TextView>(R.id.setting_fallVelo_rate)
    val noteSizeBonus_seekbar = findViewById<SeekBar>(R.id.setting_noteSizeBonus_seekbar)
    val noteSizeBonus_text = findViewById<TextView>(R.id.setting_noteSizeBonus_rate)
    val circleShrinkRate_seekbar = findViewById<SeekBar>(R.id.setting_circleShrinkRate_seekbar)
    val circleShrinkRate_text = findViewById<TextView>(R.id.setting_circleShrinkRate_rate)
    val circleLeadTime_seekbar = findViewById<SeekBar>(R.id.setting_circleLeadTime_seekbar)
    val circleLeadTime_text = findViewById<TextView>(R.id.setting_circleLeadTime_rate)
    val effectSizeBonus_seekbar = findViewById<SeekBar>(R.id.setting_effectSizeBonus_seekbar)
    val effectSizeBonus_text = findViewById<TextView>(R.id.setting_effectSizeBonus_rate)*/
    private lateinit var rgTheme: RadioGroup
    private lateinit var backgroundAlpha_seekbar: SeekBar
    private lateinit var backgroundAlpha_text: TextView
    //private val packNames = listOf("resource_phigros_replicate", "resource_sakura")
    private var packNames: List<String> = emptyList()
    private lateinit var spinner: Spinner
    private lateinit var playSpeed_seekbar: SeekBar
    private lateinit var playSpeed_text: TextView
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
        setContentView(R.layout.activity_settings)
        // 萌新开发者脚注：一定要先构筑整个场景后再调用其中的元素，否则就是java.NullPointerException咯
        rgTheme = findViewById(R.id.setting_theme)
        backgroundAlpha_seekbar = findViewById(R.id.setting_backgroundAlpha_seekbar)
        backgroundAlpha_text = findViewById(R.id.setting_backgroundAlpha_rate)
        spinner = findViewById(R.id.setting_resourcePack_spinner)
        playSpeed_seekbar = findViewById(R.id.setting_playSpeed_seekbar)
        playSpeed_text = findViewById(R.id.setting_playSpeed_rate)
        fallVelo_seekbar = findViewById(R.id.setting_fallVelo_seekbar)
        fallVelo_text = findViewById(R.id.setting_fallVelo_rate)
        noteSizeBonus_seekbar = findViewById(R.id.setting_noteSizeBonus_seekbar)
        noteSizeBonus_text = findViewById(R.id.setting_noteSizeBonus_rate)
        circleShrinkRate_seekbar = findViewById(R.id.setting_circleShrinkRate_seekbar)
        circleShrinkRate_text = findViewById(R.id.setting_circleShrinkRate_rate)
        circleLeadTime_seekbar = findViewById(R.id.setting_circleLeadTime_seekbar)
        circleLeadTime_text = findViewById(R.id.setting_circleLeadTime_rate)
        effectSizeBonus_seekbar = findViewById(R.id.setting_effectSizeBonus_seekbar)
        effectSizeBonus_text = findViewById(R.id.setting_effectSizeBonus_rate)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.settings_Toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // 显示返回箭头
        supportActionBar?.title = "设置"
        // 返回箭头点击 → 关闭本页
        toolbar.setNavigationOnClickListener { finish() }

        // ============== 外观 ================

        // ① 进来时根据当前设置勾选上
        when (SettingsManager.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> rgTheme.check(R.id.radioLight)
            AppCompatDelegate.MODE_NIGHT_YES -> rgTheme.check(R.id.radioDark)
            else -> rgTheme.check(R.id.radioSystem)
        }

        // ② 用户点击时保存并立即应用
        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            SettingsManager.themeMode = mode
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        // 初始化时把当前值填进去
        backgroundAlpha_seekbar.max = 40
        backgroundAlpha_seekbar.progress = SettingsManager.backgroundAlpha / 5
        backgroundAlpha_text.text = "${SettingsManager.backgroundAlpha} / 255"

        // 拖动时保存
        backgroundAlpha_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.backgroundAlpha = progress * 5
                    backgroundAlpha_text.text = "${progress * 5} / 255"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        /*val colorIds = listOf(
            R.id.colorOption1 to 0xFF70C000.toInt(),
            R.id.colorOption2 to 0xFF4CB6C2.toInt(),
            R.id.colorOption3 to 0xFFD0BCFF.toInt(),
            R.id.colorOption4 to 0xFFFF4B68.toInt(),
            R.id.colorOption5 to 0xFF5CA1FF.toInt(),
        )

        colorIds.forEach { (viewId, color) ->
            findViewById<View>(viewId).setOnClickListener {
                SettingsManager.primaryColor = color
                applyUserColor(findViewById(R.id.settings_root), color)
                //Toast.makeText(this, "已应用，返回后生效", Toast.LENGTH_SHORT).show()
                // 立刻重建本页让预览生效
                //recreate()
            }
        }*/


        /*spinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, packNames)

        // 进来时选中当前的
        val curIdx = packNames.indexOf(SettingsManager.resourcePack).coerceAtLeast(0)
        spinner.setSelection(curIdx)

        // 选中时保存
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsManager.resourcePack = packNames[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }*/
        refreshPackList()

        findViewById<Button>(R.id.setting_importPack_button).setOnClickListener {
            // 只显示 zip 文件
            importPackLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
        }
        findViewById<Button>(R.id.setting_managePack_button).setOnClickListener {
            showManageDialog()
        }

        // ============ 指示器基本设置 =============


        // 初始化时把当前值填进去
        playSpeed_seekbar.max = 40
        playSpeed_seekbar.progress = (SettingsManager.playSpeed * 20).toInt()
        playSpeed_text.text = " x${"%.2f".format(SettingsManager.playSpeed)}"

        // 拖动时保存
        playSpeed_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.playSpeed = progress / 20f
                    playSpeed_text.text = " x${"%.2f".format(progress / 20f)}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })



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

        val btnResetAll = findViewById<Button>(R.id.setting_resetAll_button)

        btnResetAll.setOnClickListener {
            resetAllBasicSetting()
        }

    }

    /*private fun applyUserColor(root: View, color: Int) {
        val tint = ColorStateList.valueOf(color)

        root.findViewById<RadioButton>(R.id.radioLight)?.buttonTintList = tint
        root.findViewById<RadioButton>(R.id.radioDark)?.buttonTintList = tint
        root.findViewById<RadioButton>(R.id.radioSystem)?.buttonTintList = tint

        root.findViewById<SeekBar>(R.id.setting_playSpeed_seekbar)?.apply {
            progressTintList = tint
            thumbTintList = tint
        }

        root.findViewById<Button>(R.id.setting_playSpeed_RESET)?.backgroundTintList = tint
    }*/

    private val importPackLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        doImportPack(uri)
    }

    private fun doImportPack(uri: Uri) {
        Toast.makeText(this, "开始导入…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ResourcePackImporter.importFromZip(this@SettingsActivity, uri)
            }
            when (result) {
                is ResourcePackImporter.Result.Success -> {
                    Toast.makeText(
                        this@SettingsActivity,
                        "导入成功：${result.packName}（${result.fileCount} 个文件）",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshPackList(result.packName)
                }
                is ResourcePackImporter.Result.Failure -> {
                    Toast.makeText(
                        this@SettingsActivity,
                        "导入失败：${result.reason}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showManageDialog() {
        val externalPacks = ResourcePackLoader.listExternalPacks(this)
        if (externalPacks.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("管理资源包")
                .setMessage("当前没有导入的资源包")
                .setPositiveButton("知道了", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("删除资源包（共 ${externalPacks.size} 个）")
            .setItems(externalPacks.toTypedArray()) { _, which ->
                val target = externalPacks[which]
                confirmDelete(target)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(packName: String) {
        AlertDialog.Builder(this)
            .setTitle("删除资源包")
            .setMessage("确定删除「$packName」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                val ok = ResourcePackImporter.deleteImportedPack(this, packName)
                if (ok) {
                    Toast.makeText(this, "已删除「$packName」", Toast.LENGTH_SHORT).show()
                    // ① 如果删的正好是当前选中的包，回退到第一个可用包
                    val current = SettingsManager.resourcePack
                    val remaining = ResourcePackLoader.listAllPacks(this)
                    val newSelect = if (current == packName || remaining.indexOf(current) < 0) {
                        remaining.firstOrNull()
                    } else current
                    // ② 刷新 Spinner
                    refreshPackList(newSelect)
                    // ③ 删完如果还有外部包，重新打开管理对话框
                    if (ResourcePackLoader.listExternalPacks(this).isNotEmpty()) {
                        showManageDialog()
                    }
                } else {
                    Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshPackList(selectPack: String? = null) {
        packNames = ResourcePackLoader.listAllPacks(this)
        if (packNames.isEmpty()) {
            Log.w("SettingsActivity", "当前没有任何资源包")
            return
        }

        // 暂时移除 listener，避免 setSelection 触发 onItemSelected
        spinner.onItemSelectedListener = null
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, packNames
        )

        // 优先选 selectPack，其次用当前设置，最后兜底 0
        val target = selectPack ?: SettingsManager.resourcePack
        val idx = packNames.indexOf(target).let { if (it >= 0) it else 0 }
        spinner.setSelection(idx)
        SettingsManager.resourcePack = packNames[idx]

        // 恢复 listener
        spinner.onItemSelectedListener = packSelectListener
    }

    private val packSelectListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (position in packNames.indices) {
                SettingsManager.resourcePack = packNames[position]
            }
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun resetAllBasicSetting(){
        SettingsManager.backgroundAlpha = 150
        backgroundAlpha_seekbar.progress = SettingsManager.backgroundAlpha / 5
        backgroundAlpha_text.text = "${SettingsManager.backgroundAlpha} / 255"
        SettingsManager.playSpeed = 1f
        playSpeed_seekbar.progress = (SettingsManager.playSpeed * 20).toInt()
        playSpeed_text.text = " x${"%.2f".format(SettingsManager.playSpeed)}"
        SettingsManager.fallVelo = 900
        fallVelo_seekbar.progress = (SettingsManager.fallVelo) / 25
        fallVelo_text.text = "${SettingsManager.fallVelo}px/s"
        SettingsManager.noteSizeBonus = 1f
        noteSizeBonus_seekbar.progress = (SettingsManager.noteSizeBonus * 20).toInt()
        noteSizeBonus_text.text = " x${"%.2f".format(SettingsManager.noteSizeBonus)}"
        SettingsManager.circleShrinkRate = 400
        circleShrinkRate_seekbar.progress = (SettingsManager.circleShrinkRate) / 25
        circleShrinkRate_text.text = "${SettingsManager.circleShrinkRate}px/s"
        SettingsManager.circleLeadTime = 0.75f
        circleLeadTime_seekbar.progress = (SettingsManager.circleLeadTime * 20).toInt()
        circleLeadTime_text.text = "${"%.2f".format(SettingsManager.circleLeadTime)}s"
        SettingsManager.effectSizeBonus = 1f
        effectSizeBonus_seekbar.progress = (SettingsManager.effectSizeBonus * 20).toInt()
        effectSizeBonus_text.text = " x${"%.2f".format(SettingsManager.effectSizeBonus)}"
    }
}