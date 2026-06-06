package com.wangyiheng.vcamsx

import HomeScreen
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.wangyiheng.vcamsx.services.VcamsxForegroundService
import com.wangyiheng.vcamsx.ui.theme.VCAMSXTheme

/**
 * 应用主 Activity，承载 Compose UI 入口界面。
 *
 * 负责初始化 Jetpack Compose 内容视图，展示 [HomeScreen] 主界面，
 * 并管理前台服务的启动与通知权限检查。
 */
class MainActivity : ComponentActivity() {

    /**
     * 通知设置页面返回结果回调。
     * 当用户从通知设置页面返回且通知已开启时，自动启动前台服务。
     */
    private val notificationSettingsResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (areNotificationsEnabled()) {
            startForegroundService()
        }
    }
    /**
     * Activity 创建入口，初始化 Compose UI 并展示主界面。
     *
     * @param savedInstanceState 保存的实例状态，首次创建时为 null
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        if (areNotificationsEnabled()) {
//            startForegroundService()
//        } else {
//            openNotificationSettings()
//        }
        setContent {
            VCAMSXTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background) {
                    HomeScreen()
                }
            }
        }
    }

    /**
     * 打开系统通知设置页面，引导用户开启通知权限。
     */
    private fun openNotificationSettings() {
        val intent = Intent().apply {
            action = "android.settings.APP_NOTIFICATION_SETTINGS"
            putExtra("android.provider.extra.APP_PACKAGE", packageName)
            putExtra("app_package", packageName)
            putExtra("app_uid", applicationInfo.uid)
        }
        notificationSettingsResult.launch(intent)
    }
    /**
     * 启动前台服务 [VcamsxForegroundService]。
     */
    private fun startForegroundService() {
        VcamsxForegroundService.start(this)
    }

    /**
     * 检查当前应用的通知权限是否已开启。
     *
     * @return 通知已开启返回 true，否则返回 false
     */
    private fun areNotificationsEnabled(): Boolean {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.areNotificationsEnabled()
    }

}