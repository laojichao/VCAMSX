package com.wangyiheng.vcamsx.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.FileOutputStream

/**
 * 视频帧提取与图片处理工具类。
 *
 * 提供从视频文件中按时间间隔提取帧图片，以及将 Bitmap 压缩保存到文件的功能。
 */
class Lib {
    /**
     * 从指定视频文件中按固定间隔提取帧图片。
     *
     * 使用 [MediaMetadataRetriever] 以约 10 秒间隔提取视频帧。
     *
     * @param videoPath 视频文件的绝对路径
     * @return 提取的 Bitmap 帧列表，提取失败返回空列表
     */
    fun extractFramesFromVideo(videoPath: String): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frameList = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(videoPath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            val frameRate = 10000000

            for (time in 0..duration step frameRate.toLong()) {
                val bitmap = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST)
                bitmap?.let { frameList.add(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
        return frameList
    }

    /**
     * 将 Bitmap 压缩为 JPEG 格式并保存到指定路径。
     *
     * @param bitmap 待压缩的 Bitmap 对象
     * @param outputPath 输出文件的绝对路径
     */
    fun compressAndSaveBitmap(bitmap: Bitmap, outputPath: String) {
        try {
            FileOutputStream(outputPath).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}