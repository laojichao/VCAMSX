package com.wangyiheng.vcamsx.data.models

/**
 * 视频播放状态数据模型，用于持久化用户的播放配置。
 *
 * @property isVideoEnable 视频替换功能是否开启
 * @property volume 音量是否开启
 * @property videoPlayer 视频播放器类型（1 = MediaPlayer，2 = IjkPlayer）
 * @property codecType 解码方式（false = 软解码，true = 硬解码）
 * @property isLiveStreamingEnabled 直播推流功能是否开启
 * @property liveURL RTMP 直播推流地址
 */
data class VideoStatues(
    val isVideoEnable:Boolean = false,
    val volume: Boolean = false,
    val videoPlayer:Int = 1,
    val codecType:Boolean = false,
    val isLiveStreamingEnabled:Boolean = false,
    val liveURL:String = "rtmp://ns8.indexforce.com/home/mystream"
)