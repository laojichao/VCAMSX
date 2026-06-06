package com.wangyiheng.vcamsx.data.di


import com.wangyiheng.vcamsx.utils.InfoManager
import org.koin.dsl.module
/**
 * Koin 应用级依赖注入模块。
 *
 * 提供 [InfoManager] 单例实例，用于跨进程 SharedPreferences 数据管理。
 */
val appModule = module {
    /** 提供 [InfoManager] 单例，注入 Application Context */
    single { InfoManager(get()) }
}