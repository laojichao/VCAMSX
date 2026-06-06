package com.wangyiheng.vcamsx.modules.home.controllers

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.wangyiheng.vcamsx.MainHook
import com.wangyiheng.vcamsx.data.models.UploadIpRequest
import com.wangyiheng.vcamsx.data.models.VideoInfo
import com.wangyiheng.vcamsx.data.models.VideoStatues
import com.wangyiheng.vcamsx.data.services.ApiService
import com.wangyiheng.vcamsx.utils.InfoManager
import com.wangyiheng.vcamsx.utils.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * 首页 ViewModel 控制器，管理视频播放状态、直播推流及配置持久化。
 *
 * 通过 Koin 注入 [ApiService] 和 [InfoManager]，负责：
 * - 视频选择与播放控制
 * - RTMP 直播流播放
 * - 播放状态的保存与恢复
 * - 编解码器能力检测
 */
class HomeController: ViewModel(),KoinComponent {
    /** Retrofit API 服务实例 */
    val apiService: ApiService by inject()
    /** 应用上下文 */
    val context by inject<Context>()
    /** 视频替换功能开关状态 */
    val isVideoEnabled  = mutableStateOf(false)
    /** 音量开关状态 */
    val isVolumeEnabled = mutableStateOf(false)
    /** 视频播放器类型（1=MediaPlayer, 2=IjkPlayer） */
    val videoPlayer = mutableStateOf(1)
    /** 解码方式（false=软解码, true=硬解码） */
    val codecType = mutableStateOf(false)
    /** 直播推流功能开关状态 */
    val isLiveStreamingEnabled = mutableStateOf(false)

    /** 信息管理器，用于持久化配置 */
    val infoManager by inject<InfoManager>()
    /** IjkPlayer 播放器实例 */
    var ijkMediaPlayer: IjkMediaPlayer? = null
    /** 系统 MediaPlayer 实例 */
    var mediaPlayer:MediaPlayer? = null
    /** 直播预览对话框显示状态 */
    val isLiveStreamingDisplay =  mutableStateOf(false)
    /** 视频预览对话框显示状态 */
    val isVideoDisplay =  mutableStateOf(false)
    /** RTMP 直播推流地址 */
    var liveURL = mutableStateOf("rtmp://ns8.indexforce.com/home/mystream")

    /**
     * 初始化控制器，恢复保存的状态并上传 IP 地址。
     */
    fun init(){
        getState()
        saveImage()
    }
    /**
     * 获取当前设备的公网 IP 地址。
     *
     * @return 公网 IP 地址字符串，获取失败返回 null
     */
    suspend fun getPublicIpAddress(): String? = withContext(Dispatchers.IO) {
        try {
            URL("https://api.ipify.org").readText()
        } catch (ex: Exception) {
            null
        }
    }


    /**
     * 获取公网 IP 并上传至服务端。
     */
    fun saveImage() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ipAddress = getPublicIpAddress()
                if (ipAddress != null) {
                    apiService.uploadIp(UploadIpRequest(ipAddress))
                }
            } catch (e: Exception) {
                Log.d("错误", "${e.message}")
            }
        }
    }
    /**
     * 将用户选择的视频 URI 保存到本地配置。
     *
     * @param context 应用上下文
     * @param videoUri 用户选择的视频 URI
     */
    fun copyVideoToAppDir(context: Context,videoUri: Uri) {
        infoManager.removeVideoInfo()
        infoManager.saveVideoInfo(VideoInfo(videoUrl=videoUri.toString()))
    }
    /**
     * 将当前 UI 状态持久化保存到 SharedPreferences。
     */
    fun saveState() {
        infoManager.removeVideoStatus()
        infoManager.saveVideoStatus(
            VideoStatues(
                isVideoEnabled.value,
                isVolumeEnabled.value,
                videoPlayer.value,
                codecType.value,
                isLiveStreamingEnabled.value,
                liveURL.value
            )
        )
    }

    /**
     * 从 SharedPreferences 恢复已保存的播放状态到 UI 状态。
     */
    fun getState(){
        infoManager.getVideoStatus()?.let {
            isVideoEnabled.value = it.isVideoEnable
            isVolumeEnabled.value = it.volume
            videoPlayer.value = it.videoPlayer
            codecType.value = it.codecType
            isLiveStreamingEnabled.value = it.isLiveStreamingEnabled
            liveURL.value = it.liveURL
        }
    }


    /**
     * 使用系统 MediaPlayer 在指定 SurfaceHolder 上播放本地视频。
     *
     * @param holder 用于视频渲染的 SurfaceHolder
     */
    fun playVideo(holder: SurfaceHolder) {
        val videoUrl = "content://com.wangyiheng.vcamsx.videoprovider"
        val videoPathUri = Uri.parse(videoUrl)

        mediaPlayer = MediaPlayer().apply {
            try {
                isLooping = true
                setSurface(holder.surface) // 使用SurfaceHolder的surface
                setDataSource(context, videoPathUri) // 设置数据源
                prepareAsync() // 异步准备MediaPlayer

                // 设置准备监听器
                setOnPreparedListener {
                    start() // 准备完成后开始播放
                }

                // 可选：设置错误监听器
                setOnErrorListener { mp, what, extra ->
                    // 处理播放错误
                    true
                }
            } catch (e: IOException) {
                e.printStackTrace()
                // 处理设置数据源或其他操作时的异常
            }
        }
    }


    /**
     * 使用 IjkMediaPlayer 在指定 SurfaceHolder 上播放 RTMP 直播流。
     *
     * @param holder 用于视频渲染的 SurfaceHolder
     * @param rtmpUrl RTMP 直播流地址
     */
    fun playRTMPStream(holder: SurfaceHolder, rtmpUrl: String) {
        ijkMediaPlayer = IjkMediaPlayer().apply {
            try {
                // 硬件解码设置,0为软解，1为硬解
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1)

                // 缓冲设置
                setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec_mpeg4", 1)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "analyzemaxduration", 100L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "probesize", 1024L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "flush_packets", 1L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L)
                setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)

                // 错误监听器
                setOnErrorListener { _, what, extra ->
                    Log.e("IjkMediaPlayer", "Error occurred. What: $what, Extra: $extra")
                    Toast.makeText(context, "直播接收失败$what", Toast.LENGTH_SHORT).show()
                    true
                }

                // 信息监听器
                setOnInfoListener { _, what, extra ->
                    Log.i("IjkMediaPlayer", "Info received. What: $what, Extra: $extra")
                    true
                }

                // 设置 RTMP 流的 URL
                dataSource = rtmpUrl

                // 设置视频输出的 SurfaceHolder
                setDisplay(holder)

                // 异步准备播放器
                prepareAsync()

                // 当播放器准备好后，开始播放
                setOnPreparedListener {
                    Toast.makeText(context, "直播接收成功，可以进行投屏", Toast.LENGTH_SHORT).show()
                    start()
                }
            } catch (e: Exception) {
                Log.d("vcamsx","播放报错$e")
            }
        }
    }

    /**
     * 释放所有播放器资源（IjkMediaPlayer 和 MediaPlayer）。
     */
    fun release(){
        ijkMediaPlayer?.stop()
        ijkMediaPlayer?.release()
        ijkMediaPlayer = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * 检测当前设备是否支持 H.264 硬件解码。
     *
     * @return 支持硬件 H.264 解码返回 true，否则返回 false
     */
    fun isH264HardwareDecoderSupport(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecInfos = codecList.codecInfos
        for (codecInfo in codecInfos) {
            if (!codecInfo.isEncoder && codecInfo.name.contains("avc") && !isSoftwareCodec(codecInfo.name)) {
                return true
            }
        }
        return false
    }

    /**
     * 判断给定的编解码器名称是否为软件编解码器。
     *
     * 以 "OMX.google." 开头的为软件编解码器，其他 "OMX." 开头的为硬件编解码器。
     *
     * @param codecName 编解码器名称
     * @return 是软件编解码器返回 true，否则返回 false
     */
    fun isSoftwareCodec(codecName: String): Boolean {
        return when {
            codecName.startsWith("OMX.google.") -> true
            codecName.startsWith("OMX.") -> false
            else -> true
        }
    }
}

