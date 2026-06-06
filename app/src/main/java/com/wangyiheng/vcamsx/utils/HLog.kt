package com.wangyiheng.vcamsx.utils

import android.content.Context
import android.util.Log
import de.robv.android.xposed.XposedBridge
import java.io.*
import java.util.*
import com.bigkoo.pickerview.view.WheelTime.dateFormat
/**
 * 日志工具对象，提供 Xposed 日志和本地文件日志功能。
 *
 * 支持两种日志模式：
 * - [d]：输出到 Xposed 日志（通过 [XposedBridge.log]）
 * - [localeLog]：输出到本地文件，带时间戳和间隔统计，缓冲满后批量写入
 */
object HLog {
    /** 上次日志记录的时间戳（毫秒） */
    var lastTransitionTime: Long = 0
    /** 日志消息缓冲区 */
    val logBuffer = mutableListOf<String>()
    /** 缓冲区最大容量，达到后触发文件写入 */
    val MAX_LOG_ENTRIES = 5

    /**
     * 输出 Xposed 模块日志。
     *
     * @param logtype 日志分类标签，默认为 "虚拟摄像头"
     * @param msg 日志消息内容
     */
    fun d(logtype:String?="虚拟摄像头", msg: String) {
        XposedBridge.log("$logtype:$msg")
    }
    /**
     * 记录本地日志，带时间戳和与上次日志的时间间隔。
     * 缓冲区满时自动写入外部文件。
     *
     * @param context 应用上下文，用于获取外部文件目录
     * @param msg 日志消息内容
     */
    fun localeLog(context: Context,msg:String) {
        val currentTimeMillis = System.currentTimeMillis()
        val formattedDate = dateFormat.format(Date(currentTimeMillis))

        val timeInterval = if (lastTransitionTime != 0L) {
            (currentTimeMillis - lastTransitionTime)  // 将毫秒转换为秒
        } else {
            0L
        }
        // 更新上次切换时间
        lastTransitionTime = currentTimeMillis
        val logMessage = "时间：$formattedDate\n$msg \n日志间隔时间：${timeInterval}毫秒"
        Log.d("dbb",logMessage)

        // 将日志消息添加到缓冲区
        logBuffer.add(logMessage)

        // 如果缓冲区中的日志条目达到二十条，则保存到文件并清空缓冲区
        if (logBuffer.size >= MAX_LOG_ENTRIES) {
            saveLogsToFile(context)
        }
    }
    /**
     * 将缓冲区中的日志消息批量写入外部文件并清空缓冲区。
     *
     * @param context 应用上下文，用于获取外部文件存储路径
     */
    private fun saveLogsToFile(context: Context) {
        val logFileDir = context.getExternalFilesDir(null)!!.absolutePath
        val logFilePath = File(logFileDir, "log.txt")

        try {
            // 将缓冲区中的日志消息写入文件
            logBuffer.forEach { logMessage ->
                logFilePath.appendText(logMessage + "\n\n")
            }
            // 清空缓冲区
            logBuffer.clear()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}