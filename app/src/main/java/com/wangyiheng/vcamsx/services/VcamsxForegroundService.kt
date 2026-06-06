package com.wangyiheng.vcamsx.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.wangyiheng.vcamsx.MainActivity
import com.wangyiheng.vcamsx.R

/**
 * 前台服务，用于保持应用在后台运行时的存活状态。
 *
 * 通过创建通知渠道和前台通知，确保 Xposed 模块在后台持续工作。
 * 提供静态 [start] 和 [stop] 方法供外部调用。
 */
class VcamsxForegroundService: Service()  {
    /** 通知 ID */
    private val NOTIFICATION_ID = 1
    /** 通知渠道 ID */
    private val CHANEL_ID: String = VcamsxForegroundService::class.java.getName() + ".foreground"

    /**
     * 启动前台服务（实例方法）。
     *
     * @param context 用于启动服务的上下文
     */
    fun start(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(
                Intent(
                    context,
                    VcamsxForegroundService::class.java
                )
            )
        } else {
            context.startService(Intent(context, VcamsxForegroundService::class.java))
        }
    }

    /**
     * 停止前台服务（实例方法）。
     *
     * @param context 用于停止服务的上下文
     */
    fun stop(context: Context) {
        context.stopService(Intent(context, VcamsxForegroundService::class.java))
    }

    /**
     * 服务创建时启动前台通知。
     */
    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    /**
     * 绑定服务，本服务不支持绑定，返回 null。
     *
     * @param intent 绑定意图
     * @return 始终返回 null
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForeground() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    /**
     * 构建前台通知对象。
     *
     * @return 配置完成的 [Notification] 实例
     */
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        //        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, MainActivity_.intent(this).get(), 0);
        val flags = PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        return NotificationCompat.Builder(this, CHANEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
            .setOngoing(true)
            .setSmallIcon(R.drawable.logo)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(contentIntent)
            .setChannelId(CHANEL_ID)
            .setVibrate(LongArray(0))
            .build()
    }
    /**
     * 创建通知渠道（Android O 及以上版本）。
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val manager = (getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager)
        val name: CharSequence = getString(R.string.foreground_notification_channel_name)
        val description = getString(R.string.foreground_notification_channel_name)
        val channel = NotificationChannel(CHANEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = description
        channel.enableLights(false)
        manager.createNotificationChannel(channel)
    }

    /**
     * 服务销毁时停止前台服务。
     */
    override fun onDestroy() {
        stopForeground(true)
        super.onDestroy()
    }

    companion object {
        /**
         * 启动前台服务（静态方法）。
         *
         * @param context 用于启动服务的上下文
         */
        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(
                    Intent(
                        context,
                        VcamsxForegroundService::class.java
                    )
                )
            } else {
                context.startService(Intent(context, VcamsxForegroundService::class.java))
            }
        }
        /**
         * 停止前台服务（静态方法）。
         *
         * @param context 用于停止服务的上下文
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, VcamsxForegroundService::class.java))
        }
    }
}