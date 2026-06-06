package com.wangyiheng.vcamsx.data.services;

import com.wangyiheng.vcamsx.data.models.UploadIpRequest
import com.wangyiheng.vcamsx.data.models.UploadIpResponse
import retrofit2.Response;
import retrofit2.http.*;

/**
 * 后端 API 接口定义，基于 Retrofit 注解声明 HTTP 请求。
 */
interface ApiService {
    /**
     * 上传客户端公网 IP 地址到服务端。
     *
     * @param data 包含 IP 地址的请求体
     * @return 服务端响应，包含操作结果和 IP 计数
     */
    @POST("/")
    suspend fun uploadIp(@Body data: UploadIpRequest):Response<UploadIpResponse>
}