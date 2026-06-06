package com.wangyiheng.vcamsx.utils

import com.crossbowffs.remotepreferences.RemotePreferenceProvider


/**
 * 多进程 SharedPreferences 提供器。
 *
 * 基于 [RemotePreferenceProvider] 实现跨进程 SharedPreferences 数据共享，
 * 允许主应用和 Xposed Hook 进程读写同一份配置数据。
 *
 * authority 为 "com.wangyiheng.vcamsx.preferences"，
 * 支持的偏好文件为 "main_prefs"。
 */
class MultiprocessSharedPreferences : RemotePreferenceProvider("com.wangyiheng.vcamsx.preferences", arrayOf("main_prefs"))