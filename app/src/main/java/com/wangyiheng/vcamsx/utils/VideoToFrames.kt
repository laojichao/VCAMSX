package com.wangyiheng.vcamsx.utils

import android.content.ContentValues
import android.content.ContentValues.TAG
import android.graphics.*
import android.media.*
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.wangyiheng.vcamsx.MainHook
import com.wangyiheng.vcamsx.MainHook.Companion.context
import de.robv.android.xposed.XposedBridge
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * 视频帧解码器，将视频文件解码为帧数据并输出到指定 Surface 或字节数组。
 *
 * 使用 [MediaCodec] 硬件解码器逐帧解码视频，支持：
 * - 将解码帧渲染到 Surface（用于 Camera2 预览替换）
 * - 将解码帧转换为 NV21 格式字节数组（用于 Camera1 预览回调替换）
 *
 * 解码完成后自动循环播放，直到调用 [stopDecode] 停止。
 *
 * @see OutputImageFormat
 */
class VideoToFrames : Runnable {

    /** 解码停止标志 */
    private var stopDecode = false

    /** 输出图片格式 */
    private var outputImageFormat: OutputImageFormat? = null
    /** 视频文件路径（String 或 Uri） */
    private var videoFilePath: Any? = null
    /** 解码线程 */
    private var childThread: Thread? = null
    /** 解码过程中的异常 */
    private var throwable: Throwable? = null
    /** 解码颜色格式，优先使用 YUV420Flexible */
    private val decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
    /** 视频渲染目标 Surface，为 null 时输出到字节数组 */
    private var play_surf: Surface? = null
    /** MediaCodec 超时时间（微秒） */
    private val DEFAULT_TIMEOUT_US: Long = 10000
    /** 解码回调接口 */
    private val callback: Callback? = null
    /** 帧数据队列 */
    private val mQueue: LinkedBlockingQueue<ByteArray>? = null
    /** I420 颜色格式标识 */
    private val COLOR_FormatI420 = 1
    /** NV21 颜色格式标识 */
    private val COLOR_FormatNV21 = 2
    /** 详细日志开关 */
    private val VERBOSE = false

    /**
     * 停止视频解码循环。
     */
    fun stopDecode() {
        stopDecode = true
    }

    /**
     * 解码回调接口，用于通知解码进度和完成事件。
     */
    interface Callback {
        /** 解码完成时回调 */
        fun onFinishDecode()
        /** 每帧解码完成时回调 */
        fun onDecodeFrame(index: Int)
    }

    /**
     * 设置输出图片格式。
     *
     * @param imageFormat 输出图片格式（I420、NV21 或 JPEG）
     * @throws IOException 设置失败时抛出
     */
    @Throws(IOException::class)
    fun setSaveFrames(imageFormat: OutputImageFormat) {
        outputImageFormat = imageFormat
    }

    /**
     * 设置视频渲染目标 Surface。
     *
     * @param player_surface 目标渲染 Surface
     */
    fun set_surface(player_surface:Surface){
        if(player_surface != null){
            play_surf = player_surface
        }
    }

    /**
     * 启动视频解码，在新线程中执行。
     *
     * @param videoFilePath 视频文件路径，支持 String 路径或 Uri
     */
    fun decode(videoFilePath: Any) {
        this.videoFilePath = videoFilePath
        if (childThread == null) {
            childThread = Thread(this, "decode").apply {
                start()
            }
            throwable?.let { throw it }
        }
    }

    /**
     * 解码线程入口，调用 [videoDecode] 执行实际解码。
     */
    override fun run() {
        try {
            Log.d("vcamsxtoast","------开始解码------")
            videoFilePath?.let { videoDecode(it) }
        } catch (t: Throwable) {
            throwable = t
        }
    }

    /**
     * 视频解码主逻辑，初始化 MediaExtractor 和 MediaCodec 并循环解码。
     *
     * 支持 String 路径和 Uri 两种视频源类型。解码完成后自动循环播放。
     *
     * @param videoPath 视频文件路径（String 或 Uri）
     */
    private fun videoDecode(videoPath: Any) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        try {
            extractor = MediaExtractor().apply {
                when (videoPath) {
                    is String -> setDataSource(videoPath) // 当参数是 String 时
                    is Uri -> context?.let { setDataSource(it, videoPath, null) } // 当参数是 Uri 时
                    else -> throw IllegalArgumentException("Unsupported video path type")
                }
            }
            val trackIndex = selectTrack(extractor)
            if (trackIndex < 0) {
                XposedBridge.log("&#8203;``【oaicite:5】``&#8203;&#8203;``【oaicite:4】``&#8203;No video track found in $videoFilePath")
            }
            extractor.selectTrack(trackIndex)
            val mediaFormat = extractor.getTrackFormat(trackIndex)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            decoder = MediaCodec.createDecoderByType(mime!!)
            showSupportedColorFormat(decoder.codecInfo.getCapabilitiesForType(mime))
            if (isColorFormatSupported(decodeColorFormat, decoder.codecInfo.getCapabilitiesForType(mime))) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat)
                XposedBridge.log("&#8203;``【oaicite:3】``&#8203;&#8203;``【oaicite:2】``&#8203;set decode color format to type $decodeColorFormat")
            } else {
                Log.i(ContentValues.TAG, "unable to set decode color format, color format type $decodeColorFormat not supported")
                XposedBridge.log("&#8203;``【oaicite:1】``&#8203;&#8203;``【oaicite:0】``&#8203;unable to set decode color format, color format type $decodeColorFormat not supported")
            }
            decodeFramesToImage(decoder, extractor, mediaFormat)
            decoder.stop()
            while (!stopDecode) {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                decodeFramesToImage(decoder, extractor, mediaFormat)
                decoder.stop()
            }
        } catch (e: Exception) {
            // Handle exceptions
        } finally {
            if(decoder != null) {
                decoder.stop()
                decoder.release()
                decoder = null
            }
            if(extractor != null) {
                extractor.release()
                extractor = null
            }
        }
    }
    /**
     * 从 MediaExtractor 中选择第一个视频轨道。
     *
     * @param extractor MediaExtractor 实例
     * @return 视频轨道索引，未找到返回 -1
     */
    private fun selectTrack(extractor: MediaExtractor): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime!!.startsWith("video/")) {
                return i
            }
        }
        return -1
    }

    /**
     * 打印编解码器支持的颜色格式列表。
     *
     * @param caps 编解码器能力信息
     */
    private fun showSupportedColorFormat(caps: MediaCodecInfo.CodecCapabilities) {
        for (c in caps.colorFormats) {
            print("$c\t")
        }
        println()
    }

    /**
     * 检查指定颜色格式是否被编解码器支持。
     *
     * @param colorFormat 颜色格式常量
     * @param caps 编解码器能力信息
     * @return 支持返回 true，否则返回 false
     */
    fun isColorFormatSupported(colorFormat: Int, caps: MediaCodecInfo.CodecCapabilities): Boolean {
        return caps.colorFormats.any { it == colorFormat }
    }

    /**
     * 执行帧解码循环，将视频帧渲染到 Surface 或转换为字节数组。
     *
     * 当 [play_surf] 不为 null 时，解码帧直接渲染到 Surface；
     * 否则将帧数据转换为 NV21 格式写入 [MainHook.data_buffer]。
     *
     * @param decoder MediaCodec 解码器实例
     * @param extractor MediaExtractor 实例
     * @param mediaFormat 视频轨道的媒体格式
     */
    private fun decodeFramesToImage(decoder: MediaCodec, extractor: MediaExtractor, mediaFormat: MediaFormat) {
        var isFirst = false
        var startWhen: Long = 0
        val info = MediaCodec.BufferInfo()
        decoder.configure(mediaFormat, play_surf, null, 0)
        var sawInputEOS = false
        var sawOutputEOS = false
        decoder.start()
        var outputFrameCount = 0

        while (!sawOutputEOS && !stopDecode) {
            if (!sawInputEOS) {
                val inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US)
                if (inputBufferId >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferId)
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US)
            if (outputBufferId >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
                val doRender = info.size != 0
                if (doRender) {
                    outputFrameCount++
                    callback?.onDecodeFrame(outputFrameCount)

                    if (!isFirst) {
                        startWhen = System.currentTimeMillis()
                        isFirst = true
                    }
                    if (play_surf == null) {
                        val image = decoder.getOutputImage(outputBufferId)
                        val buffer = image!!.planes[0].buffer
                        val arr = ByteArray(buffer.remaining())
                        buffer.get(arr)
                        mQueue?.put(arr)

                        if (outputImageFormat != null) {
//                            MainHook.data_buffer  =bitmapToYUV( imageToBitmap(image))
                            MainHook.data_buffer = getDataFromImage(image)
                        }
                        image.close()
                    }

                    val sleepTime = info.presentationTimeUs / 1000 - (System.currentTimeMillis() - startWhen)
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime)
                        } catch (e: InterruptedException) {
                            XposedBridge.log("&#8203;``【oaicite:1】``&#8203;" + e.toString())
                            XposedBridge.log("&#8203;``【oaicite:0】``&#8203;线程延迟出错")
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferId, true)
                }
            }
        }
        callback?.onFinishDecode()
    }

    /**
     * 打印 Image 对象的像素格式。
     *
     * @param image 待检查的 Image 对象
     */
    fun logImageFormat(image: Image) {
        val format = image.format
        val formatString = when (format) {
            ImageFormat.YUV_420_888 -> "YUV_420_888"
            ImageFormat.JPEG -> "JPEG"
            ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
            ImageFormat.NV21 -> "NV21"
            ImageFormat.YV12 -> "YV12"
            ImageFormat.RAW_PRIVATE -> "RAW_PRIVATE"
            ImageFormat.RAW10 -> "RAW10"
            ImageFormat.RAW12 -> "RAW12"
            ImageFormat.DEPTH_JPEG -> "DEPTH_JPEG"
            ImageFormat.DEPTH16 -> "DEPTH16"
            ImageFormat.DEPTH_POINT_CLOUD -> "DEPTH_POINT_CLOUD"
            // 添加更多格式根据需要
            else -> "Unknown format: $format"
        }
        Log.d("vcamsx", "Image format is $formatString")
    }

    /**
     * 将 YUV_420_888 格式的 Image 转换为 Bitmap。
     *
     * 先将 YUV 数据转为 NV21 格式，再通过 JPEG 压缩解码为 Bitmap。
     *
     * @param image YUV_420_888 格式的 Image 对象
     * @return 转换后的 Bitmap 对象
     */
    fun imageToBitmap(image: Image): Bitmap {
        Log.d("vcamsx",image.format.toString())
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // YUV_420_888数据转NV21
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)

        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

//    fun bitmapToByteArray(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
//        val stream = ByteArrayOutputStream()
//        bitmap.compress(format, quality, stream)
//        return stream.toByteArray()
//    }

    /**
     * 将 Bitmap 转换为 YUV444 格式的字节数组。
     *
     * 使用标准 RGB 到 YUV 转换公式进行色彩空间转换。
     *
     * @param bitmap 输入的 Bitmap 对象
     * @return YUV444 格式的字节数组
     */
    fun bitmapToYUV(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val intArray = IntArray(width * height)
        bitmap.getPixels(intArray, 0, width, 0, 0, width, height)

        val yuvArray = ByteArray(width * height * 3)

        var index = 0
        intArray.forEach { color ->
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // Apply the RGB to YUV formula
            val y = (0.257 * r) + (0.504 * g) + (0.098 * b) + 16
            val u = -(0.148 * r) - (0.291 * g) + (0.439 * b) + 128
            val v = (0.439 * r) - (0.368 * g) - (0.071 * b) + 128

            // Assuming the YUV format is YUV444, store each Y, U, and V value sequentially
            yuvArray[index++] = y.toInt().toByte()
            yuvArray[index++] = u.toInt().toByte()
            yuvArray[index++] = v.toInt().toByte()
        }

        return yuvArray
    }
//    private fun getDataFromImage(image: Image, colorFormat: Int): ByteArray {
//        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
//            throw IllegalArgumentException("only support COLOR_FormatI420 and COLOR_FormatNV21")
//        }
//
//        logImageFormat(image)
//        if (!isImageFormatSupported(image)) {
//            throw RuntimeException("can't convert Image to byte array, format ${image.format}")
//        }
//
//
//        val crop = image.cropRect
//        val format = image.format
//        val width = crop.width()
//        val height = crop.height()
//        val planes = image.planes
//        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
//        val rowData = ByteArray(planes[0].rowStride)
//
//        var channelOffset = 0
//        var outputStride = 1
//        for (i in planes.indices) {
//            when (i) {
//                0 -> {
//                    channelOffset = 0
//                    outputStride = 1
//                }
//                1 -> {
//                    channelOffset = if (colorFormat == COLOR_FormatI420) width * height else width * height + 1
//                    outputStride = 2
//                }
//                2 -> {
//                    channelOffset = if (colorFormat == COLOR_FormatI420) (width * height * 1.25).toInt() else width * height
//                    outputStride = 2
//                }
//            }
//            val buffer = planes[i].buffer
//            val rowStride = planes[i].rowStride
//            val pixelStride = planes[i].pixelStride
//
//            val shift = if (i == 0) 0 else 1
//            val w = width shr shift
//            val h = height shr shift
//            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
//            for (row in 0 until h) {
//                val length: Int
//                if (pixelStride == 1 && outputStride == 1) {
//                    length = w
//                    buffer.get(data, channelOffset, length)
//                    channelOffset += length
//                } else {
//                    length = (w - 1) * pixelStride + 1
//                    buffer.get(rowData, 0, length)
//                    for (col in 0 until w) {
//                        data[channelOffset] = rowData[col * pixelStride]
//                        channelOffset += outputStride
//                    }
//                }
//                if (row < h - 1) {
//                    buffer.position(buffer.position() + rowStride - length)
//                }
//            }
//        }
//        return data
//    }

    /**
     * 从 Image 对象中提取 YUV 数据并转换为 NV21 格式字节数组。
     *
     * 按 Y、V(Cr)、U(Cb) 平面顺序提取数据，处理行跨度和像素跨度。
     *
     * @param image YUV 格式的 Image 对象
     * @return NV21 格式的字节数组
     * @throws RuntimeException 当 Image 格式不支持时抛出
     */
    private fun getDataFromImage(image: Image): ByteArray {

        logImageFormat(image)
        if (!isImageFormatSupported(image)) {
            throw RuntimeException("can't convert Image to byte array, format ${image.format}")
        }

        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val pixelFormatBits = ImageFormat.getBitsPerPixel(image.format)
        val data = ByteArray(width * height * pixelFormatBits / 8)
        val rowData = ByteArray(planes[0].rowStride)

        fun copyPlaneData(planeIndex: Int, buffer: ByteBuffer, rowStride: Int, pixelStride: Int, width: Int, height: Int, channelOffset: Int, outputStride: Int) {
            var outputOffset = channelOffset
            buffer.position(rowStride * (crop.top / 2) + pixelStride * (crop.left / 2))
            for (row in 0 until height) {
                val length = if (pixelStride == 1 && outputStride == 1) {
                    width
                } else {
                    (width - 1) * pixelStride + 1
                }
                if (length == rowStride && outputStride == 1) {
                    buffer.get(data, outputOffset, length)
                    outputOffset += length
                } else {
                    buffer.get(rowData, 0, length)
                    for (col in 0 until width) {
                        data[outputOffset] = rowData[col * pixelStride]
                        outputOffset += outputStride
                    }
                }
                if (row < height - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }

        var channelOffset = 0
        val uvHeight = height / 2
        val uvWidth = width / 2

        // Y Plane
        copyPlaneData(0, planes[0].buffer, planes[0].rowStride, planes[0].pixelStride, width, height, channelOffset, 1)
        channelOffset += width * height


        copyPlaneData(1, planes[2].buffer, planes[2].rowStride, planes[2].pixelStride, uvWidth, uvHeight, channelOffset, 2)
        copyPlaneData(2, planes[1].buffer, planes[1].rowStride, planes[1].pixelStride, uvWidth, uvHeight, channelOffset + 1, 2)


        return data
    }



    /**
     * 检查 Image 的像素格式是否受支持。
     *
     * 支持的格式：YUV_420_888、NV21、YV12。
     *
     * @param image 待检查的 Image 对象
     * @return 格式受支持返回 true，否则返回 false
     */
    private fun isImageFormatSupported(image: Image): Boolean {
        val format = image.format
        Log.d("vcamsx", "format$format")
        return when (format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> true
            else -> false
        }
    }
}


/**
 * 输出图片格式枚举，定义解码帧的像素格式。
 *
 * @property friendlyName 格式的可读名称
 */
enum class OutputImageFormat(val friendlyName: String) {
    /** I420 格式（YUV 4:2:0 平面格式） */
    I420("I420"),
    /** NV21 格式（YUV 4:2:0 半平面格式，Android Camera 默认格式） */
    NV21("NV21"),
    /** JPEG 压缩格式 */
    JPEG("JPEG");

    override fun toString() = friendlyName
}

