package com.wangyiheng.vcamsx.data.models

/**
 * IP 地址上传请求数据模型。
 *
 * @property ip 待上传的客户端公网 IP 地址
 */
data class UploadIpRequest(
    val ip: String
)

/**
 * IP 地址上传响应数据模型。
 *
 * @property result 服务端返回的操作结果
 */
data class UploadIpResponse(
    val result: Result
)

/**
 * 通用操作结果数据模型。
 *
 * @property isSuccess 操作是否成功
 * @property ipcount 服务端已记录的 IP 数量
 */
data class Result(
    val isSuccess: Boolean,
    val ipcount: Int
)