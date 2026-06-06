package com.wangyiheng.vcamsx.data.services

import okhttp3.OkHttpClient
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** 后端 API 服务基础地址 */
private const val BASE_URL = "https://vcamsx.gptmanage.top/"

/**
 * Koin 网络层依赖注入模块。
 *
 * 提供 [OkHttpClient]、[Retrofit] 和 [ApiService] 的单例实例。
 */
val networkModule = module {

//    factory { ApiInterceptor(androidContext(),get<InfoManager>()) }

    single {
        OkHttpClient.Builder()
//            .addInterceptor(get<ApiInterceptor>())
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(get<OkHttpClient>())
            .build()
    }

    single { get<Retrofit>().create(ApiService::class.java) }
}