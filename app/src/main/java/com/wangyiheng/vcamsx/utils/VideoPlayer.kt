package com.wangyiheng.vcamsx.utils

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.widget.Toast
import com.wangyiheng.vcamsx.MainHook.Companion.c2_reader_Surface
import com.wangyiheng.vcamsx.MainHook.Companion.context
import com.wangyiheng.vcamsx.MainHook.Companion.oriHolder
import com.wangyiheng.vcamsx.MainHook.Companion.original_c1_preview_SurfaceTexture
import com.wangyiheng.vcamsx.MainHook.Companion.original_preview_Surface
import com.wangyiheng.vcamsx.utils.InfoProcesser.videoStatus
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 视频播放器管理对象，统一管理 Camera1/Camera2 场景下的视频播放逻辑。
 *
 * 支持本地视频播放（MediaPlayer）和 RTMP 直播流播放（IjkMediaPlayer），
 * 根据用户配置自动选择播放方式，并通过定时任务监控播放状态。
 */
object VideoPlayer {
    /** Camera2 硬件解码器实例 */
    var c2_hw_decode_obj: VideoToFrames? = null
    /** IjkPlayer 播放器实例（用于 RTMP 直播流） */
    var ijkMediaPlayer: IjkMediaPlayer? = null
    /** 系统 MediaPlayer 实例（用于本地视频） */
    var mediaPlayer: MediaPlayer? = null
    /** Camera3 播放器实例 */
    var c3_player: MediaPlayer? = null
    /** Camera2 Reader Surface 的副本引用 */
    var copyReaderSurface:Surface? = null
    /** 当前正在使用的播放 Surface */
    var currentRunningSurface:Surface? = null
    /** 定时任务执行器，用于监控播放状态 */
    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    init {
        // 初始化代码...
        startTimerTask()
    }

    /**
     * 启动定时任务，每 10 秒检查播放状态。
     */
    private fun startTimerTask() {
        scheduledExecutor.scheduleWithFixedDelay({
            // 每五秒执行的代码
            performTask()
        }, 10, 10, TimeUnit.SECONDS)
    }

    /**
     * 定时任务执行体，检查并重启 MediaPlayer。
     */
    private fun performTask() {
        restartMediaPlayer()
    }

    /**
     * 检查播放状态，当视频和直播均未启用且 Surface 有效时释放播放器。
     */
    fun restartMediaPlayer(){
        if(videoStatus?.isVideoEnable == true || videoStatus?.isLiveStreamingEnabled == true) return
        if(currentRunningSurface == null || currentRunningSurface?.isValid == false) return
        releaseMediaPlayer()
    }

    /**
     * 配置 IjkMediaPlayer 的公共监听器。
     *
     * @param mediaPlayer 待配置的 IjkMediaPlayer 实例
     */
    private fun configureMediaPlayer(mediaPlayer: IjkMediaPlayer) {
        mediaPlayer.apply {
            // 公共的错误监听器
            setOnErrorListener { _, what, extra ->
                Toast.makeText(context, "播放错误: $what", Toast.LENGTH_SHORT).show()
                true
            }

            // 公共的信息监听器
            setOnInfoListener { _, what, extra ->
                true
            }
        }
    }

    /**
     * 初始化 RTMP 直播流播放器（IjkMediaPlayer）。
     *
     * 配置硬件解码、缓冲参数和 RTMP 流地址，准备就绪后自动开始播放。
     */
    fun initRTMPStreamPlayer() {
        ijkMediaPlayer = IjkMediaPlayer().apply {
            // 硬件解码设置
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)

            // 缓冲设置
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec_mpeg4", 1)
//            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "analyzemaxduration", 100L)
             setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "analyzemaxduration", 5000L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "probesize", 2048L)
//            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "probesize", 1024L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "flush_packets", 1L)
//            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)

            Toast.makeText(context, videoStatus!!.liveURL, Toast.LENGTH_SHORT).show()

            // 应用公共配置
            configureMediaPlayer(this)

            // 设置 RTMP 流的 URL
            dataSource = videoStatus!!.liveURL

            // 异步准备播放器
            prepareAsync()

            // 准备好后的操作
            setOnPreparedListener {
                original_preview_Surface?.let { setSurface(it) }
                Toast.makeText(context, "直播接收成功", Toast.LENGTH_SHORT).show()
                start()
            }
        }
    }


    /**
     * 初始化系统 MediaPlayer 用于本地视频播放。
     *
     * @param surface 用于视频渲染的 Surface
     */
    fun initMediaPlayer(surface:Surface){
        val volume = if (videoStatus?.volume == true) 1F else 0F
        mediaPlayer = MediaPlayer().apply {
            isLooping = true
            setSurface(surface)
            setVolume(volume,volume)
            setOnPreparedListener { start() }
            val videoPathUri = Uri.parse("content://com.wangyiheng.vcamsx.videoprovider")
            context?.let { setDataSource(it, videoPathUri) }
            prepare()
        }
    }



    /**
     * 初始化视频播放状态，从 SharedPreferences 读取配置并按需启动直播播放器。
     */
    fun initializeTheStateAsWellAsThePlayer(){
        InfoProcesser.initStatus()

        if(ijkMediaPlayer == null){
            if(videoStatus?.isLiveStreamingEnabled == true){
                initRTMPStreamPlayer()
            }
        }
    }


    /**
     * 根据当前配置将视频画面渲染到指定 Surface。
     *
     * 根据视频/直播开关状态和播放器类型，选择合适的播放方式。
     *
     * @param surface 目标渲染 Surface
     */
    private fun handleMediaPlayer(surface: Surface) {
        try {
            // 数据初始化
            InfoProcesser.initStatus()

            videoStatus?.also { status ->
                if (!status.isVideoEnable && !status.isLiveStreamingEnabled) return

                val volume = if (status.volume) 1F else 0F

                when {
                    status.isLiveStreamingEnabled -> {
                        ijkMediaPlayer?.let {
                            it.setVolume(volume, volume)
                            it.setSurface(surface)
                        }
                    }
                    else -> {
                        mediaPlayer?.also {
                            if (it.isPlaying) {
                                it.setVolume(volume, volume)
                                it.setSurface(surface)
                            } else {
                                releaseMediaPlayer()
                                initMediaPlayer(surface)
                            }
                        } ?: run {
                            releaseMediaPlayer()
                            initMediaPlayer(surface)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 这里可以添加更详细的异常处理或日志记录
            logError("MediaPlayer Error", e)
        }
    }

    /**
     * 记录播放器错误日志。
     *
     * @param message 错误描述
     * @param e 异常对象
     */
    private fun logError(message: String, e: Exception) {
        // 实现日志记录逻辑，例如使用Android的Log.e函数
        Log.e("MediaPlayerHandler", "$message: ${e.message}")
    }


    /**
     * 释放 MediaPlayer 资源。
     */
    fun releaseMediaPlayer(){
        if(mediaPlayer == null)return
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * Camera2 场景下的视频播放入口。
     *
     * 分别处理带名称的预览 Surface 和 ImageReader Surface 的播放。
     */
    fun camera2Play() {
        // 带name的surface
        original_preview_Surface?.let { surface ->
            handleMediaPlayer(surface)
        }

        // name=null的surface
        c2_reader_Surface?.let { surface ->
            c2_reader_play(surface)
        }
    }

    /**
     * Camera1 场景下的视频播放入口。
     *
     * 优先使用 SurfaceTexture 创建 Surface，其次使用 SurfaceHolder 的 Surface。
     */
    fun c1_camera_play() {
        if (original_c1_preview_SurfaceTexture != null) {
            original_preview_Surface = Surface(original_c1_preview_SurfaceTexture)
            if(original_preview_Surface!!.isValid == true){
                handleMediaPlayer(original_preview_Surface!!)
            }
        }

        if(oriHolder?.surface != null){
            original_preview_Surface = oriHolder?.surface
            if(original_preview_Surface!!.isValid == true){
                handleMediaPlayer(original_preview_Surface!!)
            }
        }

        c2_reader_Surface?.let { surface ->
            c2_reader_play(surface)
        }
    }

    /**
     * Camera2 ImageReader Surface 的视频播放处理。
     *
     * 使用硬件解码器 [VideoToFrames] 将视频解码后渲染到指定 Surface。
     *
     * @param c2_reader_Surfcae Camera2 ImageReader 的目标 Surface
     */
    fun c2_reader_play(c2_reader_Surfcae:Surface){
        if(c2_reader_Surfcae == copyReaderSurface){
            return
        }

        copyReaderSurface = c2_reader_Surfcae

        if(c2_hw_decode_obj != null){
            c2_hw_decode_obj!!.stopDecode()
            c2_hw_decode_obj = null
        }

        c2_hw_decode_obj = VideoToFrames()
        try {
            val videoUrl = "content://com.wangyiheng.vcamsx.videoprovider"
            val videoPathUri = Uri.parse(videoUrl)
            c2_hw_decode_obj!!.setSaveFrames(OutputImageFormat.NV21)
            c2_hw_decode_obj!!.set_surface(c2_reader_Surfcae)
            c2_hw_decode_obj!!.decode(videoPathUri)
        }catch (e:Exception){
            Log.d("dbb",e.toString())
        }
    }

}