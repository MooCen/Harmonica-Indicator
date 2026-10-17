package com.moocengg.harmonica_indicator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import dev.atsushieno.ktmidi.Midi1CompoundMessage
import dev.atsushieno.ktmidi.Midi1Music
import dev.atsushieno.ktmidi.Midi1Status
import dev.atsushieno.ktmidi.MidiChannelStatus
import dev.atsushieno.ktmidi.MidiMetaType
import dev.atsushieno.ktmidi.read
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume


data class syllable(
    val startTick: Long,
    val pitch: Int, // 合法48到84
    var endTick: Long = -1
)
data class tempoEvent(
    val startTick: Long,
    val value: Int
)

data class INFO(
    val name: String? = "",
    val bpm: Int = 0,
    val offset: Long = 0,
    val noteCollection: List<Note> = emptyList()
)
data class Note( // 最后计算
    val startTime: Float,
    val endTime: Float,
    val type: Int,
    val funcType: Int,
    val META: String,
    val doubleRhyme: Boolean = false,
    var clickTime: Float = -1f
)

class MainActivity : AppCompatActivity() {
    // 创建一个Update
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var updateJob: Job? = null
    private var fixedUIJob: Job? = null
    private lateinit var toolBar: Toolbar
    private lateinit var nav_View: NavigationView
    private lateinit var nav_TextView: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var indicateText: TextView
    private lateinit var startOverlayButton: Button
    private lateinit var stopOverlayButton: Button

    var selectFileName: String? = null
    var midiByteContent: ByteArray?=null
    var WholeINFO: INFO = INFO("",0,0,emptyList())
    var TempoList: MutableList<tempoEvent> = mutableListOf()
    var division: Int = -1;
    var syllables: MutableList<syllable> = mutableListOf()
    var notes: MutableList<Note> = mutableListOf()
    private val pickMidiLAUNCHER = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ){uri: Uri? ->
        if(uri == null){
            Toast.makeText(this, "没有选择文件", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        readMidiFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        enableEdgeToEdge()

        AppCompatDelegate.setDefaultNightMode(SettingsManager.themeMode)

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        toolBar=findViewById(R.id.main_Toolbar)
        nav_View=findViewById(R.id.main_navigation_view)
        nav_TextView=findViewById(R.id.main_DrawerText)
        drawerLayout=findViewById(R.id.main_DrawerLayout)

        indicateText = findViewById(R.id.tvContent)

        var toggle : ActionBarDrawerToggle = ActionBarDrawerToggle(this,drawerLayout,toolBar,R.string.open,R.string.close)
        // 初始化
        toggle.syncState()
        drawerLayout.addDrawerListener(toggle)
        nav_View.setNavigationItemSelectedListener { item -> // 注册导航
            when (item.itemId) {
                R.id.nav_setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                }
                R.id.nav_indicator_advanced_setting -> {
                    startActivity(Intent(this, AdvancedSettingActivity::class.java))
                    //Toast.makeText(this, "指示器高级设置（未实现）", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_software_authority_setting -> {
                    startActivity(Intent(this, AuthoritySettingActivity::class.java))
                }
                R.id.nav_todays_character -> {
                    //今日人品
                    showTodaysCharacter()
                }
            }
            // 关闭抽屉
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val pickButton: Button = findViewById(R.id.btnPickText)
        // 绑定按钮事件
        pickButton.setOnClickListener {
            pickMidiLAUNCHER.launch("audio/midi")
        }
        startOverlayButton = findViewById(R.id.btnLaunchWindow)
        //绑定按钮事件
        startOverlayButton.setOnClickListener {
            startOverlay()
        }
        stopOverlayButton = findViewById(R.id.btnShutWindow)
        //绑定按钮事件
        stopOverlayButton.setOnClickListener {
            stopOverlay()
        }


        handleIntent(intent)


        //开始执行Update
        updateJob?.cancel()
        updateJob = scope.launch {
            awaitNextFrame()
            start()
            val startRealtime = SystemClock.elapsedRealtime()
            val startNanos = SystemClock.elapsedRealtimeNanos()
            var lastNanos = startNanos
            // 开始更新，相当于Update()
            while (isActive) {
                val now = SystemClock.elapsedRealtime() - startRealtime

                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val dtSec = (nowNanos - lastNanos) / 1_000_000_000f
                lastNanos = nowNanos
                val elapsedMs = (nowNanos - startNanos) / 1_000_000L

                try {
                    update(dtSec, (nowNanos - startNanos) / 1_000_000L)
                } catch (e: CancellationException) {
                    throw e    // 这个是协程正常取消，必须放行
                } catch (t: Throwable) {
                    Log.e("OverlayService", "update() 抛异常，本帧跳过", t)
                }

                awaitNextFrame()
            }
        }
        fixedUIJob?.cancel()
        fixedUIJob = scope.launch {
            while (isActive) {
                try {
                    fixedUIUpdate(0.1f)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e("OverlayService", "fixedUpdate 异常", t)
                }
                delay(100)   // 毫秒
            }
        }
    }
    private suspend fun awaitNextFrame() = suspendCancellableCoroutine<Unit> { cont ->
        val cb = Choreographer.FrameCallback { if (cont.isActive) cont.resume(Unit) }
        Choreographer.getInstance().postFrameCallback(cb)
        cont.invokeOnCancellation { Choreographer.getInstance().removeFrameCallback(cb) }
    }

    // 当 Activity 已经存在、又被另一个 App 拉起时会走这里（后面解释）
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
    // 统一处理外部传进来的 Uri
    private fun handleIntent(intent: Intent?){
        when(intent?.action){
            // 从文件管理器 / 微信"用其他应用打开" → ACTION_VIEW
            Intent.ACTION_VIEW -> {
                intent.data?.let { readMidiFromUri(it) }
            }
            // 从微信/QQ"分享到" → ACTION_SEND
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ 推荐写法
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { readMidiFromUri(it) }
            }

        }
    }

    // start() is called at the fist frame
    fun start(){
        //Log.i("MainActivity", "start() has been called successfully")
    }

    // update() is called per frame after start()
    var countdown: Float = 2f
    fun update(deltaTime: Float, elapsedMs: Long){
        if(OverlayState.WholeINFO.value.name==""){
            indicateText.text = "没有选择文件，前往选择"
        }else{
            indicateText.text = "${OverlayState.WholeINFO.value.name} 已就绪\nBPM: ${OverlayState.WholeINFO.value.bpm}  note数: ${OverlayState.WholeINFO.value.noteCollection.size}"
        }
    }
    fun fixedUIUpdate(fixedUIDeltaTime: Float){
        //Log.i("MainActivity", "fixedUIUpdate() has been called successfully per ${fixedUIDeltaTime}s")
        startOverlayButton.visibility =if (OverlayState.OverlayAlive) View.GONE else View.VISIBLE
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return null
    }

    private fun readMidiFromUri(uri: Uri){
        lifecycleScope.launch { // 协程
            val midiBytes: ByteArray? = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes() // 直接读取所有字节
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (midiBytes == null) {
                Toast.makeText(this@MainActivity, "读取MIDI文件失败, 得到了一个空值", Toast.LENGTH_SHORT).show()
            } else {
                // 到这里，midiBytes 就是完整的 MIDI 文件数据了
                // 下一步就是把它交给解析库
                selectFileName = getFileNameFromUri(uri)
                midiByteContent = midiBytes
                parseMidiBytes()
            }

        }
    }

    fun parseMidiBytes() {
        indicateText.text = $"文件：${selectFileName}\n状态：未解析，请重新导入"
        val bytes = midiByteContent
        if(bytes==null){
            Toast.makeText(this@MainActivity, "读取MIDI文件失败, 传入了一个空值", Toast.LENGTH_SHORT).show()
            return
        }
        RefreshData()
        val music = Midi1Music()
        music.read(bytes.toList())
        //Toast.makeText(this@MainActivity, "MIDI文件已完成读取", Toast.LENGTH_SHORT).show()

        //获取原始内容
        division = getDivisionFromByteArray(bytes)
        val Tempo = 500_000
        TempoList.add(tempoEvent(0,Tempo))
        if (division > 0) {
            //println("MIDI Division: $division")
            if (division < 0x8000) {
                //Toast.makeText(this@MainActivity, "格式: PPQ, 每四分音符 ${division} ticks", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "格式: SMPTE", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this@MainActivity, "无法解析或文件无效", Toast.LENGTH_SHORT).show()
        }
        for (track in music.tracks){
            var nowTick: Long = 0
            for (event in track.events){
                nowTick+=event.deltaTime
                //Log.i("MainActivity","当前第${nowTick}tick，消息类型码：${event.message.statusCode.toInt()}，msb值为：${event.message.msb.toInt()}")
                if((event.message.statusCode.toInt() xor MidiChannelStatus.NOTE_ON) and 0xFF == 0x00){
                    syllables.add(syllable(nowTick,event.message.msb.toInt()))
                }
                if((event.message.statusCode.toInt() xor MidiChannelStatus.NOTE_OFF) and 0xFF == 0x00){
                    for(syll in syllables.filter { it.endTick < 0 }){
                        if(syll.pitch == event.message.msb.toInt()){
                            syll.endTick=nowTick
                            if(syll.startTick==nowTick){
                                Log.w("MainActivity", "${syll.startTick}tick开始的音符长度为0")
                            }
                            break
                        }
                    }
                }
                if((event.message.statusCode.toInt() xor Midi1Status.META) and 0xFF == 0x00){ // Tempo变速事件
                    if((event.message.metaType.toInt() xor MidiMetaType.TEMPO) and 0xFF == 0x00){
                    //if((event.message.metaType.toInt() xor MidiMetaType.TEMPO) == 0xFFFFFF00.toInt()){
                        val compound = event.message as? Midi1CompoundMessage ?: continue
                        //val compound = event.message as Midi1CompoundMessage ?: continue
                        val extra = compound.extraData ?: continue
                        //val extra = compound.extraData
                        val offset = compound.extraDataOffset
                        // Tempo 是 3 个字节，表示每四分音符的微秒数
                        val microsecondsPerQuarter = Midi1Music.getSmfTempo(extra, offset)
                        if(nowTick == 0.toLong()){
                            TempoList.clear()
                        }
                        TempoList.add(tempoEvent(nowTick,microsecondsPerQuarter))
                        //Toast.makeText(this@MainActivity, "在${nowTick}tick处, 每四分音符长${microsecondsPerQuarter}微秒", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        //修正原始内容
        for(syll in syllables){
            if(syll.endTick < 0){
                syllables.remove(syll)
            }
            //Log.i("MainActivity","这个音符音高为${syll.pitch}，开始tick：${syll.startTick}, 结束tick：${syll.endTick}")
        }
        syllables = syllables.sortedBy { it.startTick }.toMutableList()
        TempoList = TempoList.sortedBy { it.startTick }.toMutableList()
        if(TempoList.size==1){
            Toast.makeText(this@MainActivity, "整首曲子无变速，BPM:${(60000000.0 / TempoList[0].value).toInt()}，总共有${syllables.size}个音符", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this@MainActivity, "整首曲子中有${TempoList.size-1}处变速", Toast.LENGTH_SHORT).show()
        }

        indicateText.text = $"文件：${selectFileName}\n状态：已解析"

        // 生成Note序列
        var (lastTuneGear: Int, lastSemitone: Boolean) = 0 to false
        Log.i("MainActivity","当前tempo值为：${TempoList[0].value}, Midi文件division为：${division}")
        for (syll in syllables){
            var tuneGear = 0
            // 48-59 为降调 48、50、52、53、55、57、59
            // 60-71 为自然音 60、62、64、65、67、69、71
            // 72-83 为升调 72、74、76、77、79、81、83
            // 84 为 C6 1''
            val (lane, semitone) = CalcPitchNum(syll.pitch)
            var metaInfo: String = ""
            var doubleRhyme = 0
            if(semitone){
                metaInfo+="#"
            }
            metaInfo+="${lane}"
            if (syll.pitch in 48..59){
                tuneGear = -1
                metaInfo += "."
            }else if (syll.pitch in 72..84){
                tuneGear = 1
                metaInfo += "'"
                if (syll.pitch == 84){
                    metaInfo += "'"
                }
            }
            if(tuneGear!=lastTuneGear)doubleRhyme+=1
            if(semitone!=lastSemitone)doubleRhyme+=1
            if(tuneGear!=lastTuneGear){
                notes.add(Note(
                    CalcTimePoint(syll.startTick),
                    CalcTimePoint(syll.startTick),
                    3-tuneGear,
                    2,
                    "tuneGear",
                    doubleRhyme>=2)
                )
                //Log.i("MainActivity","生成了一个变调提示note")
            }
            if(semitone!=lastSemitone){
                notes.add(Note(
                    CalcTimePoint(syll.startTick),
                    CalcTimePoint(syll.startTick),
                    1,
                    2,
                    "semitone",
                    doubleRhyme>=2)
                )
                //Log.i("MainActivity","生成了一个半音提示note")
            }
            notes.add(Note(
                CalcTimePoint(syll.startTick),
                CalcTimePoint(syll.endTick),
                lane,
                1,
                metaInfo,
                doubleRhyme>=1)
            )

            lastTuneGear = tuneGear
            lastSemitone = semitone
        }
        //Log.i("MainActivity", "最后一个有效音符的开始时间：${notes[notes.size-1].startTime}")
        WholeINFO = INFO(name = selectFileName,(60000000.0 / TempoList[0].value).toInt(), noteCollection = notes)
        indicateText.text = $"文件：${selectFileName}\n状态：已就绪"
        transmitINFO2Service()
    }

    fun CalcPitchNum(pitch: Int) : Pair<Int, Boolean>{
        //接受0-11,返回1-7
        var ans : Int = -1
        var semi: Boolean = false
        val quotient = pitch / 12
        val remainder = pitch % 12
        if(remainder in 0..1){
            ans=1
            if(remainder == 1){
                semi = true
            }
        }else if(remainder in 2..3){
            ans=2
            if(remainder == 3){
                semi = true
            }
        }else if(remainder in 4..4){
            ans=3
        }else if(remainder in 5..6){
            ans=4
            if(remainder == 6){
                semi = true
            }
        }else if(remainder in 7..8){
            ans=5
            if(remainder == 8){
                semi = true
            }
        }else if(remainder in 9..10){
            ans=6
            if(remainder == 10){
                semi = true
            }
        }else if(remainder in 11..12){
            ans=7
        }
        if(remainder == 0 && quotient == 7){
            ans=8
            semi=false
        }
        return ans to semi
    }
    fun CalcTimePoint(tick: Long) : Float{
        if(division <= 0){
            return -1f
        }
        var cnt: Long = 0
        var ans: Float = 0f
        var topIndex: Int = -1
        for (tempoEvent in TempoList){
            if(tempoEvent.startTick<=tick){
                topIndex++
            }
        }
        for (i in 0..topIndex-1){
            cnt+=(TempoList[i+1].startTick-TempoList[i].startTick)*TempoList[i].value
            ans+=(TempoList[i+1].startTick-TempoList[i].startTick)*TempoList[i].value/(division*1000000f)
        }
        cnt+=(tick-TempoList[topIndex].startTick)*TempoList[topIndex].value
        ans+=(tick-TempoList[topIndex].startTick)*TempoList[topIndex].value/(division*1000000f)
        //ans = cnt/(division*1000000f)
        //Log.i("MainActivity","计算得到时间：${ans}")
        return ans
    }

    fun RefreshData(){
        TempoList = mutableListOf()
        syllables = mutableListOf()
        notes = mutableListOf()
    }


    fun transmitINFO2Service(){
        OverlayState.updateINFO(WholeINFO)
        OverlayState.updateNotes(notes)
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
            return
        }
        startOverlayButton.visibility = View.GONE
        //startService(Intent(this, OverlayService::class.java))
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
    }

    private fun stopOverlay() {
        startOverlayButton.visibility = View.VISIBLE
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun showTodaysCharacter() {
        val value = TodaysCharacterHelper.getTodayValue(this)
        //val value = 100 // 666不演了
        if (value == 0) {
            // 先弹免责声明
            AlertDialog.Builder(this)
                .setTitle("今日人品 - 附加使用条款")
                .setMessage(
                    "在查看结果前，请先同意以下附加使用条款：\n\n" +
                            "1. 我知晓并了解本应用的「今日人品」功能完全没有出 Bug。\n" +
                            "2. 本应用不对使用本软件间接造成的一切财产损失（如砸手机等）等负责。"
                )
                .setPositiveButton("同意并查看结果") { _, _ ->
                    showCharacterResult(value)
                }
                .setNegativeButton("再见", null)
                .show()
        } else {
            showCharacterResult(value)
        }
    }

    private fun showCharacterResult(value: Int) {
        var ceiling = " / 100"
        val comment = TodaysCharacterHelper.getComment(value)
        if(value <= 20){
            ceiling = "……"
        }
        if(value <= 10){
            ceiling = " ？！"
        }
        if(value <= 0){
            ceiling = "...\n？"
        }
        if(value >= 80){
            ceiling = " / 100~"
        }
        if(value >= 90){
            ceiling = " / 100！"
        }
        if(value >= 99){
            ceiling = " / 100！！！"
        }
        AlertDialog.Builder(this)
            .setTitle("今日人品")
            .setMessage("人品值：$value$ceiling\n\n$comment")
            .setPositiveButton("知道了", null)
            .show()
    }

}



fun getDivisionFromByteArray(midiData: ByteArray): Int {
    // MIDI 文件头至少要有 14 字节才能包含 division 字段
    // MThd(4) + ChunkSize(4) + Format(2) + NTrks(2) + Division(2) = 14 bytes
    if (midiData.size < 14) {
        return -1
    }

    // 验证文件头标识 "MThd"
    if (midiData[0] != 'M'.code.toByte() ||
        midiData[1] != 'T'.code.toByte() ||
        midiData[2] != 'h'.code.toByte() ||
        midiData[3] != 'd'.code.toByte()) {
        return -1 // 不是标准的 MIDI 文件
    }

    // Division 字段位于第 12 和 13 字节（索引从 0 开始）
    // MIDI 使用大端序 (Big-Endian)
    val byte1 = midiData[12].toInt() and 0xFF
    val byte2 = midiData[13].toInt() and 0xFF

    // 组合成 16 位整数
    val division = (byte1 shl 8) or byte2

    return division
}

/** 从 Tempo Meta 事件中提取 BPM */
private fun extractBpm(music: Midi1Music): Int {
    for (track in music.tracks) {
        for (event in track.events) {
            val message = event.message
            // 检查是否为 Meta 事件，且类型为 Tempo (0x51)
            if (message.statusCode.toInt() == Midi1Status.META &&
                message.metaType.toInt() == MidiMetaType.TEMPO
            ) {
                // 需要将消息转换为 Midi1CompoundMessage 才能读取 extraData
                val compound = message as? Midi1CompoundMessage ?: continue
                val extra = compound.extraData ?: continue
                val offset = compound.extraDataOffset
                // Tempo 是 3 个字节，表示每四分音符的微秒数
                val microsecondsPerQuarter = Midi1Music.getSmfTempo(extra, offset)
                return (60000000.0 / microsecondsPerQuarter).toInt()
            }
        }
    }
    return 120 // 如果没有找到 Tempo 事件，返回默认的 120 BPM
}

/** 提取音轨名称 (Track Name Meta 事件, 类型 0x03) */
private fun extractTrackName(music: Midi1Music): String? {
    for (track in music.tracks) {
        for (event in track.events) {
            val message = event.message
            if (message.statusCode.toInt() == Midi1Status.META &&
                message.metaType.toInt() == 0x03 // Track Name
            ) {
                val compound = message as? Midi1CompoundMessage ?: continue
                val extra = compound.extraData ?: continue
                val offset = compound.extraDataOffset
                val length = compound.extraDataLength
                return String(extra, offset, length, Charsets.UTF_8)
            }
        }
    }
    return null
}