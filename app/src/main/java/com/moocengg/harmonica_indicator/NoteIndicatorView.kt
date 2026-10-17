package com.moocengg.harmonica_indicator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import java.io.InputStream

/*data class HitEffect(
    val startTimeSec: Float,   // 特效开始的时刻，等于 note 的 clickTime
    val x: Float,              // 屏幕坐标（NoteIndicatorView 局部坐标系）
    val y: Float,
    val size: Float            // 渲染边长，你自己调
)*/
class HitEffect {
    var startTimeSec: Float = 0f
    var x: Float = 0f
    var y: Float = 0f
    var size: Float = 0f

    // 池内状态标记。true = 正在使用（不要回收），false = 空闲
    var inUse: Boolean = false
}

/**
 * 极简对象池，仅适用于「单线程、频繁 acquire/release」的场景。
 * 不是线程安全的，别在多线程里用。
 */
class HitEffectPool(initialCapacity: Int = 128) {

    // 空闲对象栈。用 ArrayDeque 避免中途扩容
    private val free = ArrayDeque<HitEffect>(initialCapacity)

    init {
        // 预先铺好一批对象，第一次播放时零分配
        repeat(initialCapacity) { free.addLast(HitEffect()) }
    }

    /** 借一个对象并填好字段，标记为使用中。池空了就临时 new 一个（很少发生）。 */
    fun acquire(startTimeSec: Float, x: Float, y: Float, size: Float): HitEffect {
        val e = free.removeLastOrNull() ?: HitEffect()
        e.startTimeSec = startTimeSec
        e.x = x
        e.y = y
        e.size = size
        e.inUse = true
        return e
    }

    /** 归还对象。重复归还会被忽略（防止逻辑出错时池被污染）。 */
    fun release(effect: HitEffect) {
        if (!effect.inUse) return
        effect.inUse = false
        free.addLast(effect)
    }
}

class NoteIndicatorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    lateinit var calibrationContainer: FrameLayout
    var pointDict = mapOf<Pair<Int, Int>,View>()
    // 每帧被 OverlayService 更新
    var currentTimeMs: Long = 0L

    // 音乐信息与音符列表：从 OverlayState 同步
    var WholeINFO: INFO = INFO("",0,0,emptyList())
    var notes: List<Note> = emptyList()

    // 设置
    var playSpeed: Float = 1f
    // 下落速度：像素/秒。300f 表示 1 秒走 300 像素
    var fallVelo: Int = 900
    var noteSizeBonus: Float = 1f
    var circleShrinkRate: Float = 400f
    var circleLeadTime: Float = 1f

    var effectSizeBonus: Float = 1f
    // alpha 范围：刚出现时用 minAlpha，到点时用 maxAlpha
    var circleAlphaMin: Int = 40
    var circleAlphaMax: Int = 255
    var noteLineSpeed: Float = 1f
    var funcLineSpeed: Float = 1f
    var noteLineType: Int = 1 // 1线型 2圈型
    var funcLineType: Int = 1
    var noteLineNoteType: Int = 1
    var noteLineReverse: Boolean = false // 反转下落
    var funcLineNoteType: Int = 3
    var funcLineReverse: Boolean = false


    // 目标点在 NoteIndicatorView 局部坐标系里的位置
    val pointLoc = IntArray(2)
    val trackLoc = IntArray(2)
    var pointScaleX = 1f
    var pointScaleY = 1f
    var targetX: Float = 0f
    var targetY: Float = 0f

    // 判定线：
    val noteLineX = resources.displayMetrics.widthPixels.toFloat()
    var noteLineY = -1f
    val funcLineX = resources.displayMetrics.widthPixels.toFloat()
    var funcLineY = -1f

    var colorPerfect : Int = 0xFFfcfdb4.toInt()
    var colorGood : Int = 0xFF78d8fc.toInt()

    // 判定线：实心主线
    private val lineCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPerfect  // 纯金
        strokeWidth = 16f * (pointScaleX)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // 判定线长度（像素，即线中心向左右各延伸多少）
    private val lineHalfLength = 2000f
    // 圆圈出现的提前量：只在这个时间窗口内显示。单位：秒
        // 比如 1.5f 表示"startTime 前 1.5 秒才出现"
    // 音符note图片
    // 不同表达方式
    private var noteFuncType2FuncLineSpeed = mapOf<Int, Float>(1 to noteLineSpeed, 2 to funcLineSpeed)
    private var noteFuncType2LineTypeMap = mapOf<Int, Int>(1 to noteLineType, 2 to funcLineType)
    private var noteFuncType2NoteDrawableMap = mapOf<Int, Int>(1 to noteLineNoteType, 2 to funcLineNoteType)
    private var lineType2ReverseMap = mapOf<Int, Boolean>(1 to noteLineReverse, 2 to funcLineReverse)
    // ====== 音符图片（延迟加载，从资源包读）======
    private val noteDrawableDict = mutableMapOf<Pair<Int, Boolean>, Drawable>()
    private val noteBitmapCache = mutableMapOf<Pair<Int, Boolean>, Bitmap>()

    // 音符图片名映射：funcType to doubleRhyme → 文件名
    private val noteFileNames = mapOf(
        (1 to false) to "click.png",
        (1 to true) to "click_mh.png",
        (2 to false) to "drag.png",
        (2 to true) to "drag_mh.png",
        (3 to false) to "flick.png",
        (3 to true) to "flick_mh.png",
        (4 to false) to "hold.png",
        (4 to true) to "hold_mh.png",
    )


    // 切换note
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPerfect   // 和判定线同色
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }
    private val circlePaintDoubleRhyme = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGood
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }


    // ====== 特效资源（延迟加载）======
    var resourcePackName: String = ""
        private set
    var packInfo: ResourcePackInfo = ResourcePackInfo()
        private set

    private var effectInputStream: InputStream? = null
    private var effectRegionDecoder: BitmapRegionDecoder? = null

    private var sheetCols: Int = 1
    private var sheetRows: Int = 1
    private var sheetFrameCount: Int = 1
    private var frameW: Int = 0
    private var frameH: Int = 0

    private val effectFps = 90f

    // ★帧缓存：每个帧索引只解码一次，避免 onDraw 里每帧重复解码
    private val frameBitmapCache = HashMap<Int, Bitmap>()

    //private val activeEffects = mutableListOf<HitEffect>()
    // ★ 对象池：预分配 128 个，够同时 100+ 个特效同时存在
    private val effectPool = HitEffectPool(initialCapacity = 128)

    // ★ 活跃列表预先给好容量，避免 add() 时扩容
    private val activeEffects = ArrayList<HitEffect>(128)
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        colorFilter = PorterDuffColorFilter(
            colorPerfect,
            PorterDuff.Mode.SRC_IN
        )
    }

    var dstRect = RectF(0f, 0f, 0f, 0f)

    /**
     * 加载指定名字的资源包。
     * 会自动读 `info.yml` 里的 hitFx 配置并打开精灵图。
     * 建议在 `preloadEffectFrames()` 之前、主线程上调用一次。
     */
    fun loadResourcePack(packName: String) {
        //已加载，跳过重载
        if (packName == resourcePackName && effectRegionDecoder != null) {
            Log.i("NoteIndicatorView", "资源包「$packName」已加载，跳过重新加载")
            return
        }

        releaseEffectResources()
        resourcePackName = packName

        val info = ResourcePackLoader.load(context, packName)
        if (info == null) {
            Log.e("NoteIndicatorView", "资源包 $packName 读取失败，特效将不可用")
            return
        }
        packInfo = info
        // 加载音符图片
        loadNoteDrawables(packName)
        sheetCols = info.hitFxCols.coerceAtLeast(1)
        sheetRows = info.hitFxRows.coerceAtLeast(1)
        sheetFrameCount = sheetCols * sheetRows

        val stream = ResourcePackLoader.openHitFxImage(context, packName) ?: return
        try {
            effectInputStream = stream
            effectRegionDecoder = BitmapRegionDecoder.newInstance(stream, false)
            frameW = (effectRegionDecoder?.width ?: 0) / sheetCols
            frameH = (effectRegionDecoder?.height ?: 0) / sheetRows
            Log.i(
                "NoteIndicatorView",
                "资源包「${info.name}」加载成功：" +
                        "${sheetCols}x${sheetRows}，单帧 ${frameW}x${frameH}，" +
                        "图集 ${effectRegionDecoder?.width}x${effectRegionDecoder?.height}"
            )
        } catch (e: Exception) {
            Log.e("NoteIndicatorView", "创建 regionDecoder 失败", e)
            releaseEffectResources()
        }

        // 后处理颜色
        colorPerfect = info.colorPerfect?: 0xFFFCFDB4.toInt()
        colorGood = info.colorGood?: 0xFF78D8FC.toInt()
        Log.i("NoteIndicatorView","读取到的colorPerfect:${info.colorPerfect}, colorGood:${info.colorGood}")
        Log.i("NoteIndicatorView","使用的colorPerfect:${colorPerfect}, colorGood:${colorGood}")
        // 重新应用到所有相关 Paint
        lineCorePaint.color = colorPerfect
        circlePaint.color = colorPerfect
        circlePaintDoubleRhyme.color = colorGood
        effectPaint.colorFilter = PorterDuffColorFilter(colorPerfect, PorterDuff.Mode.SRC_IN)
    }

    /**
     * 从资源包加载音符图片。
     * 缺哪张就跳过哪张，不抛异常。加载失败时绘制会退回到跳过该音符。
     */
    private fun loadNoteDrawables(packName: String) {
        // 清掉旧的
        noteBitmapCache.values.forEach { it.recycle() }
        noteBitmapCache.clear()
        noteDrawableDict.clear()

        for ((key, fileName) in noteFileNames) {
            val bmp = try {
                ResourcePackLoader.openPackFile(context, packName, fileName)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                Log.w("NoteIndicatorView", "音符图片读取异常: $packName/$fileName", e)
                null
            }
            if (bmp == null) {
                Log.w("NoteIndicatorView", "音符图片缺失，跳过: $packName/$fileName")
                continue
            }
            noteBitmapCache[key] = bmp
            noteDrawableDict[key] = BitmapDrawable(resources, bmp)
        }
        /*for ((key, fileName) in noteFileNames) {
            val path = "resource_packs_phigros_theme/$packName/$fileName"
            val bmp = try {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                Log.w("NoteIndicatorView", "音符图片缺失，跳过: $path")
                null
            }
            if (bmp == null) continue

            noteBitmapCache[key] = bmp
            noteDrawableDict[key] = BitmapDrawable(resources, bmp)
        }*/
        Log.i("NoteIndicatorView", "音符图片加载完成：${noteDrawableDict.keys}")
    }

    /** 释放所有特效资源（帧缓存、解码器、输入流）。幂等，重复调用安全。 */
    fun releaseEffectResources() {
        frameBitmapCache.values.forEach { it.recycle() }
        frameBitmapCache.clear()
        // 释放音符图片
        noteBitmapCache.values.forEach { it.recycle() }
        noteBitmapCache.clear()
        noteDrawableDict.clear()

        effectRegionDecoder?.recycle()
        effectRegionDecoder = null

        try { effectInputStream?.close() } catch (_: Exception) {}
        effectInputStream = null

        sheetCols = 1
        sheetRows = 1
        sheetFrameCount = 1
        frameW = 0
        frameH = 0
    }

    private fun getFrameBitmap(frameIndex: Int): Bitmap? {
        frameBitmapCache[frameIndex]?.let { return it }
        val decoder = effectRegionDecoder ?: return null
        if (frameW <= 0 || frameH <= 0) return null

        val col = frameIndex % sheetCols
        val row = frameIndex / sheetCols
        val rect = Rect(
            col * frameW,
            row * frameH,
            (col + 1) * frameW,
            (row + 1) * frameH
        )
        // 双保险：别越界
        if (rect.right > decoder.width || rect.bottom > decoder.height) {
            Log.w("NoteIndicatorView", "帧 $frameIndex 越界: $rect / ${decoder.width}x${decoder.height}")
            return null
        }
        return try {
            decoder.decodeRegion(rect, null)?.also { frameBitmapCache[frameIndex] = it }
        } catch (e: Exception) {
            Log.e("NoteIndicatorView", "解码特效帧 $frameIndex 失败", e)
            null
        }
    }

    fun syncINFO(){
        calibrationContainer = OverlayState.calibrationContainer.value
        pointDict = OverlayState.pointDict
        WholeINFO = OverlayState.WholeINFO.value
        notes = OverlayState.notes.value

        // ★ 重置所有音符的 clickTime 与活跃特效，让新一轮播放能重新触发特效
        notes.forEach { it.clickTime = -1f }
        noteFuncType2FuncLineSpeed = mapOf<Int, Float>(1 to noteLineSpeed, 2 to funcLineSpeed)
        noteFuncType2LineTypeMap = mapOf<Int, Int>(1 to noteLineType, 2 to funcLineType)
        noteFuncType2NoteDrawableMap = mapOf<Int, Int>(1 to noteLineNoteType, 2 to funcLineNoteType)
        lineType2ReverseMap = mapOf<Int, Boolean>(1 to noteLineReverse, 2 to funcLineReverse)
        // ★ 把活跃特效全部归还池中，再清空列表
        activeEffects.forEach { effectPool.release(it) }
        activeEffects.clear()

        OverlayState.updateCombo(false,0)

        this.getLocationOnScreen(trackLoc)
        pointScaleX= calibrationContainer.scaleX
        pointScaleY= calibrationContainer.scaleY
        pointDict[1 to 1]?.getLocationOnScreen(pointLoc)
        noteLineY = pointLoc[1] - trackLoc[1] + (pointDict[1 to 1]?.height ?: 1) * pointScaleY / 2f
        pointDict[1 to 2]?.getLocationOnScreen(pointLoc)
        funcLineY = pointLoc[1] - trackLoc[1] + (pointDict[1 to 2]?.height ?: 1) * pointScaleY / 2f
        //Log.i("NoteIndicatorView", "noteTrack位置：${trackLoc.contentToString()}, 音轨布局大小：${calibrationContainer.scaleX},${calibrationContainer.scaleY}")
        Log.i("NoteIndicatorView", "第一判定点长宽：${pointDict[1 to 1]?.width},${pointDict[1 to 1]?.height}")
        Log.i("NoteIndicatorView", "第一判定点大小：${pointDict[1 to 1]?.scaleX},${pointDict[1 to 1]?.scaleY}")
    }

    // 音符图片渲染大小（像素）
    // private var noteSizeX: Int = (noteDrawable?.getIntrinsicWidth() ?: 0) / 10
    // private var noteSizeY: Int = (noteDrawable?.getIntrinsicHeight() ?: 0) / 10
    private var noteSizeX: Int = 1000
    private var noteSizeY: Int = 100

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 判定线
        // 实心主线
        if(noteLineType == 1) canvas.drawLine(noteLineX - lineHalfLength, noteLineY, noteLineX + lineHalfLength, noteLineY, lineCorePaint)
        if(funcLineType == 1) canvas.drawLine(funcLineX - lineHalfLength, funcLineY, noteLineX + lineHalfLength, funcLineY, lineCorePaint)

        // note

        if(noteDrawableDict.isEmpty()){
            Log.e("NoteIndicatorView", "音符图片未加载，跳过本帧绘制")
            return
        }
        var drawable: Drawable
        //Log.i("NoteIndicatorView", "currentTimeSec: ${currentTimeMs/1000f}")
        //Log.i("NoteIndicatorView", "当前曲目：${WholeINFO.name}")
        //Log.i("NoteIndicatorView", "当前曲目note数：${notes.size}")
        for (note in notes) {
            //drawable = noteDrawableDict[note.funcType to note.doubleRhyme] ?: continue
            drawable = noteDrawableDict[noteFuncType2NoteDrawableMap[note.funcType] to if(note.funcType==1) (note.doubleRhyme) else true] ?: continue
            //val bmp = noteBitmapCache[note.funcType to note.doubleRhyme] ?: continue
            val bmp = noteBitmapCache[noteFuncType2NoteDrawableMap[note.funcType] to if(note.funcType==1) (note.doubleRhyme) else true] ?: continue
            noteSizeX = (bmp.width * pointScaleX / 7).toInt()
            noteSizeY = (bmp.height * pointScaleY / 7).toInt()

            val point = pointDict[note.type to note.funcType]
            if(point==null){
                //Log.w("NoteIndicatorView","functype为${note.funcType}，type为${note.type}的音符所在点未能成功找到位置")
                continue
            }
            //计算目标位置
            point.getLocationOnScreen(pointLoc)
            targetX = (pointLoc[0] - trackLoc[0] + point.width * pointScaleX / 2f)
            targetY = (pointLoc[1] - trackLoc[1] + point.height * pointScaleY / 2f)

            if (noteFuncType2LineTypeMap[note.funcType]==2) {
            //if (note.funcType == 2) {
                val dt = note.startTime / playSpeed - currentTimeMs / 1000f   // 还剩几秒

                if (currentTimeMs / 1000f > note.startTime / playSpeed) {
                    if(note.clickTime<0){
                        note.clickTime = currentTimeMs/1000f

                        // 生成一个特效实例
                        val effectSize = 200f * pointScaleX * effectSizeBonus
                        activeEffects.add(
                            effectPool.acquire(
                                startTimeSec = note.clickTime,
                                x = targetX,
                                y = targetY,
                                size = effectSize
                            )
                        )
                        /*val effectSize = 300f   // 特效显示大小，你看效果调
                        activeEffects.add(
                            HitEffect(
                                startTimeSec = note.clickTime,
                                x = targetX,
                                y = targetY,
                                size = effectSize
                            ))*/
                    }
                    continue
                }
                // ① 出现窗口：只画 [0, leadTime] 之间的圆圈
                if (dt < 0f || dt > circleLeadTime) continue

                // ② 半径：dt 越小半径越小，dt==0 时等于目标半径
                val targetR = point.width * calibrationContainer.scaleX / 2.2f
                //val targetR = 0
                val radius = targetR + dt * circleShrinkRate

                // ③ 归一化进度：0 = 刚进入窗口，1 = 到点
                val progress = 1f - (dt / circleLeadTime).coerceIn(0f, 1f)

                // ④ alpha 随 progress 线性从 min 到 max
                val alpha = (circleAlphaMin + (circleAlphaMax - circleAlphaMin) * progress)
                    .toInt().coerceIn(0, 255)

                // ⑤ 应用 alpha 并绘制
                circlePaint.alpha = alpha



                canvas.drawCircle(targetX, targetY, radius, if(note.doubleRhyme) circlePaintDoubleRhyme else circlePaint)
                continue
            }
            else{
                // 当前时间超过 endTime → 生命周期结束
                //if (currentTimeMs/1000f > note.endTime / playSpeed) {
                if (currentTimeMs / 1000f > note.startTime / playSpeed) {
                    if(note.clickTime<0){
                        note.clickTime = currentTimeMs/1000f
                        OverlayState.updateCombo(true)

                        // 生成一个特效实例     ——咱要不后边改用对象池？
                        val effectSize = 200f * pointScaleX * effectSizeBonus
                        activeEffects.add(
                            effectPool.acquire(
                                startTimeSec = note.clickTime,
                                x = targetX,
                                y = targetY,
                                size = effectSize
                            )
                        )
                        /*val effectSize = 300f   // 特效显示大小
                        activeEffects.add(
                            HitEffect(
                                startTimeSec = note.clickTime,
                                x = targetX,
                                y = targetY,
                                size = effectSize
                            ))*/
                    }
                    continue
                }

                // 距离"该按键"还剩多少毫秒。正数=还没到，负数=已经过了
                val dt = note.startTime / playSpeed - currentTimeMs / 1000f

                // 位置公式：目标点 + (currentTime - startTime) * 速度
                // 等价于：目标点 - dt * 速度
                val y = targetY - dt * fallVelo * (noteFuncType2FuncLineSpeed[note.funcType]?:1f) * if(lineType2ReverseMap[note.funcType]?:false) -1 else 1
                //val y = 0

                // 屏幕外跳过（上下都留一点余量，避免刚进屏幕时被裁造成人工上隐）
                if (y < -noteSizeY || y > height + noteSizeY) continue

                // 用图片的实际宽高比来画，保持等比
                val halfX = noteSizeX * pointScaleX * noteSizeBonus / 2f
                val halfY = noteSizeY * pointScaleY * noteSizeBonus / 2f
                drawable.setBounds(
                    (targetX - halfX).toInt(),
                    (y - halfY).toInt(),
                    (targetX + halfX).toInt(),
                    (y + halfY).toInt()
                )
                if (lineType2ReverseMap[note.funcType] ?: false) {
                    canvas.save()
                    canvas.scale(1f, -1f, targetX, y)   // 以 (targetX, y) 为中心上下翻转
                    drawable.draw(canvas)
                    canvas.restore()
                } else {
                    drawable.draw(canvas)
                }
            }

        }
        // 绘制所有活跃特效
        if (effectRegionDecoder != null) {
            val frameDuration = 1f / effectFps
            val totalDuration = sheetFrameCount * frameDuration

            val iter = activeEffects.iterator()
            while (iter.hasNext()) {
                val effect = iter.next()
                val elapsed = currentTimeMs / 1000f - effect.startTimeSec

                if (elapsed >= totalDuration) {
                    effectPool.release(effect)   // ★ 先归还，再移除
                    iter.remove()
                    continue
                }
                /*if (elapsed >= totalDuration) {
                    iter.remove()
                    continue
                }*/

                val frameIndex = (elapsed / frameDuration).toInt()
                    .coerceIn(0, sheetFrameCount - 1)

                val frameBitmap = getFrameBitmap(frameIndex) ?: continue

                val half = effect.size / 2f
                dstRect.left = effect.x - half
                dstRect.top = effect.y - half
                dstRect.right = effect.x + half
                dstRect.bottom = effect.y + half

                canvas.drawBitmap(frameBitmap, null, dstRect, effectPaint)
            }
        }
    }

    private fun calculatePointPosByCalibPoint(lane: Int, funcType: Int): IntArray{
        var calibPointLoc = IntArray(2)
        val calibPoint = pointDict[lane to funcType]
        if(calibPoint==null){
            Log.w("NoteIndicatorView","没找到校准点，使用(0,0)")
            return intArrayOf(0,0)
        }
        calibPoint.getLocationOnScreen(calibPointLoc)
        val ans = intArrayOf(
            (calibPointLoc[0] - trackLoc[0] + calibPoint.width * pointScaleX / 2f).toInt(),
            (calibPointLoc[1] - trackLoc[1] + calibPoint.height * pointScaleY / 2f).toInt()
        )
        return ans
    }

    fun preloadEffectFrames(startIndex: Int = 0, endIndex: Int = -1) {
        val decoder = effectRegionDecoder
        if (decoder == null) {
            Log.w("NoteIndicatorView", "预热失败：解码器为空")
            return
        }
        if (frameW <= 0 || frameH <= 0) {
            Log.w("NoteIndicatorView", "预热失败：帧尺寸非法 ${frameW}x${frameH}")
            return
        }

        val realEnd = if (endIndex < 0) sheetFrameCount else endIndex.coerceAtMost(sheetFrameCount)
        val start = startIndex.coerceAtLeast(0)
        if (start >= realEnd) return

        val t0 = System.currentTimeMillis()
        var decoded = 0
        for (i in start until realEnd) {
            if (frameBitmapCache.containsKey(i)) continue
            val col = i % sheetCols
            val row = i / sheetCols
            val rect = Rect(
                col * frameW,
                row * frameH,
                (col + 1) * frameW,
                (row + 1) * frameH
            )
            if (rect.right > decoder.width || rect.bottom > decoder.height) {
                Log.w("NoteIndicatorView", "预热跳过越界帧 $i: $rect")
                continue
            }
            try {
                decoder.decodeRegion(rect, null)?.let {
                    frameBitmapCache[i] = it
                    decoded++
                }
            } catch (e: Exception) {
                Log.e("NoteIndicatorView", "预热解码帧 $i 失败", e)
            }
        }
        Log.i(
            "NoteIndicatorView",
            "预热完成：解码 $decoded 帧，耗时 ${System.currentTimeMillis() - t0} ms，" +
                    "缓存共 ${frameBitmapCache.size} 帧"
        )
    }

    /** 清空帧缓存，释放内存。资源包切换或长时间不用时可以调。 */
    fun clearEffectFrameCache() {
        frameBitmapCache.values.forEach { it.recycle() }
        frameBitmapCache.clear()
    }


    /*override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        frameBitmapCache.values.forEach { it.recycle() }
        frameBitmapCache.clear()
        effectRegionDecoder?.recycle()
        try { effectInputStream?.close() } catch (_: Exception) {}
    }*/
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseEffectResources()
    }
}