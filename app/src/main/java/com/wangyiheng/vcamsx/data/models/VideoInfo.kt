package com.wangyiheng.vcamsx.data.models

/**
 * 视频信息数据模型，用于存储用户选择的视频元数据。
 *
 * @property videoId 视频唯一标识，默认为 0
 * @property videoName 视频名称，默认为 "vcamsx"
 * @property videoUrl 视频文件路径或 URI，默认为空字符串
 * @property videoType 视频格式类型，默认为 "mp4"
 */
data class VideoInfo(
    val videoId: Int = 0,
    val videoName: String = "vcamsx",
    val videoUrl: String ="",
    val videoType: String = "mp4"
)

/**
 * 视频信息列表包装类。
 *
 * @property videos 视频信息列表
 */
data class VideoInfos(
    val videos: List<VideoInfo>
)