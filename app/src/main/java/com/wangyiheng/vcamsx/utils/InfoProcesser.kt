package com.wangyiheng.vcamsx.utils

import com.wangyiheng.vcamsx.MainHook
import com.wangyiheng.vcamsx.data.models.VideoStatues

/**
 * 信息处理器对象，负责在 Xposed Hook 环境中初始化和缓存视频播放状态。
 *
 * 通过 [InfoManager] 从跨进程 SharedPreferences 读取配置，
 * 供 Hook 逻辑快速访问当前视频播放状态。
 */
object InfoProcesser {
    /** 缓存的视频播放状态 */
    var videoStatus: VideoStatues? = null
    /** 信息管理器实例 */
    var infoManager : InfoManager?= null

    /**
     * 初始化视频播放状态，从 SharedPreferences 读取并缓存。
     *
     * @throws IllegalStateException 当 [MainHook.context] 为 null 时
     */
    fun initStatus(){
        infoManager = InfoManager(MainHook.context!!)
        videoStatus = infoManager!!.getVideoStatus()
    }
}