package com.moocengg.harmonica_indicator

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    var viewLoadedNow: Boolean = false
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var viewBackground: View
    private lateinit var overlayControlPad: View
    private lateinit var textHint: TextView
    private lateinit var btnEditMode: Button
    private lateinit var btnPlay: Button
    private lateinit var btnStartCalibration: Button
    private lateinit var btnResetCalibration: Button
    private lateinit var layoutPauseRaycast: FrameLayout
    private lateinit var btnPause: Button
    private lateinit var pgbPause: ProgressBar
    private lateinit var noteTrack: NoteIndicatorView
    private lateinit var touchInterceptorView: FrameLayout
    private lateinit var calibrationView: FrameLayout

    // 校准界面设置
    private var initialTransX = 0f
    private var initialTransY = 0f
    private var initialScale = 1f

    private var activePointerId = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    //Dict<(lane,funcType),View>
    var pointDict = mapOf<Pair<Int, Int>,View>()

    // 捏合缩放检测器
    private lateinit var scaleDetector: ScaleGestureDetector

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var playJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("OverlayService", "onStartCommand, intent=$intent, flags=$flags, startId=$startId")
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        OverlayState.OverlayAlive = true
        Log.d("OverlayService", "onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.service_note_indicator_overlay_view, null)
        viewBackground = overlayView.findViewById(R.id.viewBackground)
        overlayControlPad = overlayView.findViewById(R.id.overlayControlPad)
        textHint = overlayView.findViewById(R.id.textHint)
        btnPlay = overlayView.findViewById(R.id.btnPlay)
        btnEditMode = overlayView.findViewById(R.id.btnEditMode)
        btnStartCalibration = overlayView.findViewById(R.id.btnStartCalibration)
        btnResetCalibration = overlayView.findViewById(R.id.btnResetCalibration)
        layoutPauseRaycast = overlayView.findViewById(R.id.layoutPauseRaycast)
        btnPause = overlayView.findViewById(R.id.btnPauseRaycast)
        pgbPause = overlayView.findViewById(R.id.pgbPauseRaycast)
        noteTrack = overlayView.findViewById(R.id.noteTrack)
        syncSetting()
        overlayView.setBackgroundColor("#${String.format("%02X", SettingsManager.backgroundAlpha)}000000".toColorInt())
        noteTrack.preloadEffectFrames()                 // ← 必须放在 loadResourcePack 之后
        touchInterceptorView = overlayView.findViewById(R.id.touchInterceptorView)
        calibrationView = touchInterceptorView.findViewById(R.id.calibrationContainer)
        //calibrationView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        setupWindow()
        bindUI()
        setupCalibrationGestures()
        pointDict = mapOf<Pair<Int, Int>,View>(
            1 to 1 to overlayView.findViewById<View>(R.id.calibPoint1)
            , 2 to 1 to overlayView.findViewById<View>(R.id.calibPoint2)
            , 3 to 1 to overlayView.findViewById<View>(R.id.calibPoint3)
            , 4 to 1 to overlayView.findViewById<View>(R.id.calibPoint4)
            , 5 to 1 to overlayView.findViewById<View>(R.id.calibPoint5)
            , 6 to 1 to overlayView.findViewById<View>(R.id.calibPoint6)
            , 7 to 1 to overlayView.findViewById<View>(R.id.calibPoint7)
            , 8 to 1 to overlayView.findViewById<View>(R.id.calibPoint8)
            , 1 to 2 to overlayView.findViewById<View>(R.id.calibPointSemitone)
            , 2 to 2 to overlayView.findViewById<View>(R.id.calibPointUpTone)
            , 3 to 2 to overlayView.findViewById<View>(R.id.calibPointNaturalTone)
            , 4 to 2 to overlayView.findViewById<View>(R.id.calibPointDownTone)
        )
        OverlayState.pointDict=pointDict
        initialTransX = calibrationView.translationX
        initialTransY = calibrationView.translationY
        initialScale = calibrationView.scaleX
        calibrationView.translationX = SettingsManager.lastCalibrationPosX
        calibrationView.translationY = SettingsManager.lastCalibrationPosY
        calibrationView.scaleX = if(SettingsManager.lastCalibrationScaleX > 0) SettingsManager.lastCalibrationScaleX else calibrationView.scaleX
        calibrationView.scaleY = if(SettingsManager.lastCalibrationScaleY > 0) SettingsManager.lastCalibrationScaleY else calibrationView.scaleY
        observeState()
    }

    private fun setupWindow() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,   // 遮挡阶段
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  // 先不加 NOT_TOUCHABLE，按钮要能点
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE // 开始时强制横屏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alpha = 0.8f   // 12+ 必须 ≤ 0.8 才能让 NOT_TOUCHABLE 生效
            }
        }
        windowManager.addView(overlayView, params)
        viewLoadedNow = true
        pgbPause.visibility = View.GONE
        //layoutPauseRaycast.visibility = View.GONE
        toggleEditMode(1)
    }

    private fun bindUI() {
        btnPlay.setOnClickListener { startPlayback() }
        btnEditMode.setOnClickListener { toggleEditMode() }
        btnStartCalibration.setOnClickListener { toggleCalibrationPad() }
        btnResetCalibration.setOnClickListener { resetCalibration() }
        btnPause.setOnClickListener { pauseRaycast() }
    }


    // 2. 修改 setupCalibrationGestures() 方法
    private fun setupCalibrationGestures() {
        //Log.i("OverlayService","音轨布局初始大小：${calibrationView.scaleX},${calibrationView.scaleY}")
        scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    // 注意：这里操作的是 calibrationContainer 的缩放
                    val newScale = (calibrationView.scaleX * detector.scaleFactor)
                        .coerceIn(0.2f, 5f)
                    calibrationView.scaleX = newScale
                    calibrationView.scaleY = newScale
                    return true
                }
            })

        // 3. 将监听器绑定到 touchInterceptorView 上
        touchInterceptorView.setOnTouchListener { _, event ->
            if (!calibrationView.isVisible) return@setOnTouchListener false
            if (kotlin.math.abs(calibrationView.translationX)>=resources.displayMetrics.widthPixels.toFloat()/1.5f
                || kotlin.math.abs(calibrationView.translationY)>=resources.displayMetrics.heightPixels.toFloat()/1.5f){
                textHint.text="检查你的校准图片\n它貌似位置异常"
            }else{
                textHint.text="调整校准图片\n直至与你的游戏界面重合"
            }
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!calibrationView.isVisible) return@setOnTouchListener false
                    // 记住是哪根手指开始拖的
                    activePointerId = event.getPointerId(0)
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // 有新手指加入（可能在按按钮），暂停拖动，避免活动指针乱跳
                    activePointerId = -1
                }
                MotionEvent.ACTION_MOVE -> {
                    // 没有锁定手指 / 正在捏合 → 不拖动
                    if (activePointerId == -1) return@setOnTouchListener true
                    if (scaleDetector.isInProgress) return@setOnTouchListener true

                    // 只取"我们锁定的那根手指"的坐标，无视活动指针
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx < 0) return@setOnTouchListener true

                    val x = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        event.getRawX(idx)
                    } else {
                        event.rawX
                        TODO("VERSION.SDK_INT < Q")
                    }
                    val y = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        event.getRawY(idx)
                    } else {
                        event.rawY
                        TODO("VERSION.SDK_INT < Q")
                    }
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY

                    // 加个边界限制，图不会被拖到完全看不见
                    val screenW = resources.displayMetrics.widthPixels.toFloat()
                    val screenH = resources.displayMetrics.heightPixels.toFloat()
                    calibrationView.translationX =
                        (calibrationView.translationX + dx).coerceIn(-screenW, screenW)
                    calibrationView.translationY =
                        (calibrationView.translationY + dy).coerceIn(-screenH, screenH)

                    lastTouchX = x
                    lastTouchY = y
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    // 一根手指抬起，如果还剩一根，把它作为新的拖动手指
                    if (event.pointerCount == 2) {
                        val upIdx = event.actionIndex
                        val remainIdx = if (upIdx == 0) 1 else 0
                        activePointerId = event.getPointerId(remainIdx)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            lastTouchX = event.getRawX(remainIdx)
                        }else{
                            lastTouchX = event.rawX
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            lastTouchY = event.getRawY(remainIdx)
                        }else{
                            lastTouchY = event.rawY
                        }
                    } else {
                        activePointerId = -1
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 手指全部离开，或者事件被系统取消，清空状态
                    activePointerId = -1
                }
            }
            true
        }
        /*touchInterceptorView.setOnTouchListener { _, event ->
            if (!calibrationView.isVisible) return@setOnTouchListener false

            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                        calibrationView.translationX += event.rawX - lastTouchX
                        calibrationView.translationY += event.rawY - lastTouchY
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val remainIndex = if (event.actionIndex == 0) 1 else 0
                    if (remainIndex < event.pointerCount) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            lastTouchX = event.getRawX(remainIndex)
                        }else{
                            lastTouchX = event.rawX
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            lastTouchY = event.getRawY(remainIndex)
                        }else{
                            lastTouchY = event.rawY
                        }
                    }
                }
            }
            true
        }*/
    }

    private fun startPlayback() {
        if(OverlayState.WholeINFO.value.name==""&&OverlayState.WholeINFO.value.bpm==0){
            Toast.makeText(this@OverlayService, "当前暂未选择曲目，选择后可开始播放", Toast.LENGTH_SHORT).show()
            return
        }


        toggleCalibrationPad(-1)
        //touchInterceptorView.visibility = View.VISIBLE
        touchInterceptorView.visibility = View.INVISIBLE
        SettingsManager.lastCalibrationPosX = calibrationView.translationX
        SettingsManager.lastCalibrationPosY = calibrationView.translationY
        SettingsManager.lastCalibrationScaleX = calibrationView.scaleX
        SettingsManager.lastCalibrationScaleY = calibrationView.scaleY
        OverlayState.calibrationContainer = MutableStateFlow<FrameLayout>(calibrationView)
        overlayControlPad.visibility = View.GONE
        textHint.visibility = View.VISIBLE
        btnPlay.visibility = View.GONE
        //viewBackground.visibility = View.VISIBLE
        noteTrack.visibility = View.VISIBLE
        overlayView.setBackgroundColor("#00000000".toColorInt())
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        viewBackground.alpha = 0.8f
        windowManager.updateViewLayout(overlayView, params)

        // 把音符推给 view
        //noteTrack.notes = OverlayState.notes.value
        syncSetting()
        noteTrack.syncINFO()

        //Log.i("OverlayService", "发起绘制notes请求")
        //Log.i("OverlayService", "发起绘制notes请求, 第十个note的出现时间为${OverlayState.notes.value[9].startTime}")
        noteTrack.invalidate()

        playJob?.cancel()
        playJob = scope.launch {
            val startRealtime = SystemClock.elapsedRealtime()
            val startNanos = SystemClock.elapsedRealtimeNanos()
            var lastNanos = startNanos
            // 开始更新，相当于Update()
            while (isActive) {
                val now = SystemClock.elapsedRealtime() - startRealtime - 3000
                OverlayState.updateCurrentTime(now)

                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val dtSec = (nowNanos - lastNanos) / 1_000_000_000f
                lastNanos = nowNanos
                val elapsedMs = (nowNanos - startNanos) / 1_000_000L

                try {
                    updatePlaying(dtSec, (nowNanos - startNanos) / 1_000_000L)
                } catch (e: CancellationException) {
                    throw e    // 这个是协程正常取消，必须放行
                } catch (t: Throwable) {
                    Log.e("OverlayService", "update() 抛异常，本帧跳过", t)
                }

                awaitNextFrame()
            }
        }
    }
    private suspend fun awaitNextFrame() = suspendCancellableCoroutine<Unit> { cont ->
        val cb = Choreographer.FrameCallback { if (cont.isActive) cont.resume(Unit) }
        Choreographer.getInstance().postFrameCallback(cb)
        cont.invokeOnCancellation { Choreographer.getInstance().removeFrameCallback(cb) }
    }

    private fun observeState() {
        scope.launch {
            OverlayState.currentTimeMs.collect { t ->
                //noteTrack.currentTimeMs = (t * noteTrack.playSpeed).toLong()
                noteTrack.currentTimeMs = t
                noteTrack.invalidate()
            }
        }
        // notes 变化：现在还没用上，先订阅着
        // scope.launch { OverlayState.notes.collect { ... } }
    }

    private fun buildNotification(): Notification {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "悬浮提示", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("谱面提示运行中")
            .setContentText("点击返回 App 可停止")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    fun syncSetting(){
        noteTrack.loadResourcePack(SettingsManager.resourcePack)
        noteTrack.playSpeed = SettingsManager.playSpeed
        noteTrack.fallVelo = SettingsManager.fallVelo
        noteTrack.noteSizeBonus = SettingsManager.noteSizeBonus
        noteTrack.circleShrinkRate = SettingsManager.circleShrinkRate.toFloat()
        noteTrack.circleLeadTime = SettingsManager.circleLeadTime
        noteTrack.effectSizeBonus = SettingsManager.effectSizeBonus
        noteTrack.circleAlphaMin = SettingsManager.circleAlphaMin
        noteTrack.circleAlphaMax = SettingsManager.circleAlphaMax
        noteTrack.noteLineSpeed = SettingsManager.noteLineSpeed
        noteTrack.funcLineSpeed = SettingsManager.funcLineSpeed
        noteTrack.noteLineType = SettingsManager.noteLineType
        noteTrack.funcLineType = SettingsManager.funcLineType
        noteTrack.noteLineNoteType = SettingsManager.noteLineNoteType
        noteTrack.noteLineReverse = SettingsManager.noteLineReverse
        noteTrack.funcLineNoteType = SettingsManager.funcLineNoteType
        noteTrack.funcLineReverse = SettingsManager.funcLineReverse
    }

    private fun updatePlaying(deltaTime: Float, elapsedMs: Long){
        textHint.text="${OverlayState.combo.value} combo"
    }

    private fun toggleEditMode(swi: Int = 0){
        val showing = if(swi==0) !btnStartCalibration.isVisible else (swi > 0)
        textHint.visibility = if (showing) View.VISIBLE else View.GONE
        btnStartCalibration.visibility = if (showing) View.VISIBLE else View.GONE
        btnEditMode.text = if (showing) "模式：全屏编辑" else "模式：小窗预览"
        if(showing){
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            windowManager.updateViewLayout(overlayView, params)
        }else{
            toggleCalibrationPad(-1)
            touchInterceptorView.visibility = View.GONE
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            windowManager.updateViewLayout(overlayView, params)
        }

    }
    private fun toggleCalibrationPad(swi: Int = 0) {
        val showing = if(swi==0) (!touchInterceptorView.isVisible) else (swi > 0)
        touchInterceptorView.visibility = if (showing) View.VISIBLE else View.INVISIBLE
        btnResetCalibration.visibility = if (showing) View.VISIBLE else View.GONE
        btnStartCalibration.text = if (showing) "隐藏校准界面" else "呼出校准界面"
        if (showing) {
            if (!(kotlin.math.abs(calibrationView.translationX)>=resources.displayMetrics.widthPixels.toFloat()/1.5f
                        || kotlin.math.abs(calibrationView.translationY)>=resources.displayMetrics.heightPixels.toFloat()/1.5f)){

                textHint.text = "调整校准图片\n直至与你的游戏界面重合"
            }
        }else{
            if (!(kotlin.math.abs(calibrationView.translationX)>=resources.displayMetrics.widthPixels.toFloat()/1.5f
                || kotlin.math.abs(calibrationView.translationY)>=resources.displayMetrics.heightPixels.toFloat()/1.5f)){

                textHint.text = "准备开始\n${OverlayState.WholeINFO.value.name}"
            }
        }
    }
    private fun resetCalibration() {
        calibrationView.translationX = initialTransX
        calibrationView.translationY = initialTransY
        calibrationView.scaleX = initialScale
        calibrationView.scaleY = initialScale

        SettingsManager.lastCalibrationPosX = calibrationView.translationX
        SettingsManager.lastCalibrationPosY = calibrationView.translationY
        SettingsManager.lastCalibrationScaleX = calibrationView.scaleX
        SettingsManager.lastCalibrationScaleY = calibrationView.scaleY
    }

    private var cooldownAnimator: ValueAnimator? = null
    private fun pauseRaycast(){
        if (cooldownAnimator?.isRunning == true) return
        //val alpha = viewBackground.alpha
        //viewBackground.alpha = 0.4f
        pgbPause.progress = 0
        pgbPause.visibility = View.VISIBLE

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager.updateViewLayout(overlayView, params)

        cooldownAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = 6_000
            interpolator = LinearInterpolator()   // 线性，不缓动
            addUpdateListener { anim ->
                pgbPause.progress = anim.animatedValue as Int
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if(viewLoadedNow){
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        windowManager.updateViewLayout(overlayView, params)
                        //viewBackground.alpha = alpha
                        pgbPause.visibility = View.GONE
                    }else{
                        return
                    }
                }
            })
            start()
        }



    }

    override fun onDestroy() {
        OverlayState.OverlayAlive = false
        Log.d("OverlayService", "onDestroy")
        playJob?.cancel()
        scope.cancel()
        noteTrack.releaseEffectResources()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        viewLoadedNow = false
        super.onDestroy()
    }

    companion object { const val NOTIF_ID = 1001 }
}