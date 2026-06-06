package com.wangyiheng.vcamsx.utils

import android.content.Context
import com.crossbowffs.remotepreferences.RemotePreferences
import com.wangyiheng.vcamsx.data.models.VideoStatues
import com.google.gson.Gson
import com.wangyiheng.vcamsx.data.models.VideoInfo

/**
 * 信息管理器，封装跨进程 SharedPreferences 的视频状态和视频信息读写操作。
 *
 * 基于 [RemotePreferences] 实现跨进程数据共享，使用 Gson 进行 JSON 序列化/反序列化。
 *
 * @param context 应用上下文，用于初始化 RemotePreferences
 */
class InfoManager(context: Context) {
    /** 跨进程 SharedPreferences 实例 */
    val prefs = RemotePreferences(context, "com.wangyiheng.vcamsx.preferences", "main_prefs")
    /** Gson 序列化实例 */
    private val gson = Gson()

    /**
     * 保存视频播放状态到 SharedPreferences。
     *
     * @param videoStatus 待保存的视频状态对象
     */
    fun saveVideoStatus(videoStatus: VideoStatues) {
        val jsonString = gson.toJson(videoStatus)
        prefs.edit().putString("videoStatus", jsonString).apply()
    }

    /**
     * 从 SharedPreferences 读取视频播放状态。
     *
     * @return 已保存的 [VideoStatues] 对象，未保存过返回 null
     */
    fun getVideoStatus(): VideoStatues? {
        val jsonString = prefs.getString("videoStatus", null)
        return if (jsonString != null) {
            gson.fromJson(jsonString, VideoStatues::class.java)
        } else {
            null
        }
    }

    /**
     * 从 SharedPreferences 移除视频播放状态。
     */
    fun removeVideoStatus() {
        prefs.edit().remove("videoStatus").apply()
    }

    /**
     * 保存视频信息到 SharedPreferences。
     *
     * @param videoInfo 待保存的视频信息对象
     */
    fun saveVideoInfo(videoInfo: VideoInfo) {
        val jsonString = gson.toJson(videoInfo)
        prefs.edit().putString("videoInfo", jsonString).apply()
    }

    /**
     * 从 SharedPreferences 读取视频信息。
     *
     * @return 已保存的 [VideoInfo] 对象，未保存过返回 null
     */
    fun getVideoInfo(): VideoInfo? {
        val jsonString = prefs.getString("videoInfo", null)
        return if (jsonString != null) {
            gson.fromJson(jsonString, VideoInfo::class.java)
        } else {
            null
        }
    }

    /**
     * 从 SharedPreferences 移除视频信息。
     */
    fun removeVideoInfo() {
        prefs.edit().remove("videoInfo").apply()
    }
}