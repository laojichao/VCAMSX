package com.wangyiheng.vcamsx.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.wangyiheng.vcamsx.modules.home.controllers.HomeController

/**
 * 视频预览播放对话框 Composable 组件。
 *
 * 当 [HomeController.isVideoDisplay] 为 true 时显示，
 * 使用 SurfaceView 和 MediaPlayer 播放用户选择的本地视频。
 *
 * @param homeController 首页控制器，提供视频播放状态和控制方法
 */
@Composable
fun VideoPlayerDialog(homeController: HomeController) {
    if (homeController.isVideoDisplay.value) {
        Dialog(onDismissRequest = {
            homeController.isVideoDisplay.value = false
        }) {
            Column(
                modifier = Modifier.size(width = 300.dp, height = 400.dp), // 设置Dialog的大小
                verticalArrangement = Arrangement.Center
            ) {
                AndroidView(
                    modifier = Modifier.weight(1f), // 让视频播放器填充除按钮以外的空间
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    homeController.playVideo(holder)
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    // 这里释放播放器资源
                                    homeController.release()
                                }
                            })
                        }
                    }
                )
            }
        }
    }
}