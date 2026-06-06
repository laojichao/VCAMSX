package com.wangyiheng.vcamsx.utils

import android.util.Log
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.util.*

/**
 * IjkMediaPlayer 播放器池管理器。
 *
 * 维护一个固定大小的播放器对象池（默认 5 个），通过 [acquirePlayer] 获取播放器，
 * 使用完毕后自动回收重置，减少频繁创建和销毁播放器的开销。
 */
object MediaPlayerManager {
    /** 播放器池最大容量 */
    private const val MAX_PLAYER_COUNT = 5
    /** 播放器对象队列 */
    private val playerQueue = LinkedList<IjkMediaPlayer>()

    init {
        // 初始化播放器队列
        repeat(MAX_PLAYER_COUNT) {
            val mediaPlayer = IjkMediaPlayer()
            mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0)


            playerQueue.add(mediaPlayer)
        }
    }

    /** 当前正在使用的播放器实例 */
    private var currentPlayingPlayer: IjkMediaPlayer? = null

    /**
     * 获取一个可用的 IjkMediaPlayer 实例。
     *
     * 如果当前有正在使用的播放器，会先释放并回收。优先从池中获取，
     * 池为空时创建新实例。
     *
     * @return 可用的 [IjkMediaPlayer] 实例
     */
    fun acquirePlayer(): IjkMediaPlayer {
        // 释放之前的播放器对象
        Log.d("dbb",playerQueue.toString())
        currentPlayingPlayer?.let {
            releasePlayer(it)
        }


        return if (playerQueue.isNotEmpty()) {
            currentPlayingPlayer = playerQueue.poll() // 获取可用的播放器对象并设置为当前播放器
            currentPlayingPlayer!!
        } else {
            currentPlayingPlayer = IjkMediaPlayer() // 如果队列为空，创建一个新的播放器对象并设置为当前播放器
            currentPlayingPlayer!!
        }
    }

    /**
     * 释放播放器并回收到池中。
     *
     * @param player 待释放的播放器实例，为 null 时忽略
     */
    private fun releasePlayer(player: IjkMediaPlayer?) {
        player?.apply {
            reset()
            playerQueue.offer(this) // 重置播放器并放回队列中
        }
    }
}
