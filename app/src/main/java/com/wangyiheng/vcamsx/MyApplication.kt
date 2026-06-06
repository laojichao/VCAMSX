package com.wangyiheng.vcamsx

import android.app.Application
import com.wangyiheng.vcamsx.data.di.appModule
import com.wangyiheng.vcamsx.data.services.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * 自定义 Application 类，负责应用级别的初始化工作。
 *
 * 在应用启动时初始化 Koin 依赖注入框架，注册 [appModule] 和 [networkModule] 模块，
 * 为全局依赖注入提供上下文环境。
 */
class MyApplication : Application() {
    /**
     * Application 创建入口，初始化 Koin 依赖注入框架。
     */
    override fun onCreate() {
        super.onCreate()
        // Initialize Koin
        startKoin {
            // Declare modules to use
            androidContext(this@MyApplication)
            modules(appModule,networkModule)
        }
    }
}