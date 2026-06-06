package com.wangyiheng.vcamsx

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.Toast
import com.wangyiheng.vcamsx.utils.HLog
import com.wangyiheng.vcamsx.utils.InfoProcesser.videoStatus
import com.wangyiheng.vcamsx.utils.OutputImageFormat
import com.wangyiheng.vcamsx.utils.VideoPlayer.c1_camera_play
import com.wangyiheng.vcamsx.utils.VideoPlayer.ijkMediaPlayer
import com.wangyiheng.vcamsx.utils.VideoPlayer.camera2Play
import com.wangyiheng.vcamsx.utils.VideoPlayer.initializeTheStateAsWellAsThePlayer
import com.wangyiheng.vcamsx.utils.VideoToFrames
import de.robv.android.xposed.*
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.min


/**
 * Xposed 模块主入口类，负责 Hook 目标应用的摄像头相关方法。
 *
 * 通过拦截 Camera1/Camera2 API，将目标应用的摄像头预览数据替换为用户指定的视频源，
 * 实现虚拟摄像头功能。支持 Camera1（[Camera]）和 Camera2（[CameraDevice]）两种 API。
 *
 * @see IXposedHookLoadPackage
 */
class MainHook : IXposedHookLoadPackage {
    companion object {
        /** 日志标签 */
        val TAG = "vcamsx"
        /** 经过解码后用于替换的视频帧数据缓冲区 */
        @Volatile
        var data_buffer = byteArrayOf(0)
        /** 当前 Hook 目标应用的上下文 */
        var context: Context? = null
        /** Camera1 原始预览摄像头实例引用 */
        var origin_preview_camera: Camera? = null
        /** Camera1 用于拦截 setPreviewTexture 的伪造 SurfaceTexture */
        var fake_SurfaceTexture: SurfaceTexture? = null
        /** Camera1 setPreviewDisplay 场景下的伪造 SurfaceTexture */
        var c1FakeTexture: SurfaceTexture? = null
        /** Camera1 setPreviewDisplay 场景下的伪造 Surface */
        var c1FakeSurface: Surface? = null

        /** Camera2 原始 SessionConfiguration 引用 */
        var sessionConfiguration: SessionConfiguration? = null
        /** Camera2 原始 OutputConfiguration 引用 */
        var outputConfiguration: OutputConfiguration? = null
        /** Camera2 替换后的伪造 SessionConfiguration */
        var fake_sessionConfiguration: SessionConfiguration? = null

        /** Camera2 原始预览 Surface 引用 */
        var original_preview_Surface: Surface? = null
        /** Camera1 原始预览 SurfaceTexture 引用 */
        var original_c1_preview_SurfaceTexture:SurfaceTexture? = null
        /** 视频播放器是否正在播放 */
        var isPlaying:Boolean = false
        /** 是否需要重新创建虚拟 Surface */
        var needRecreate: Boolean = false
        /** Camera2 虚拟 SurfaceTexture 实例 */
        var c2VirtualSurfaceTexture: SurfaceTexture? = null
        /** Camera2 ImageReader 使用的 Surface */
        var c2_reader_Surface: Surface? = null
        /** Camera1 onPreviewFrame 回调对应的摄像头实例 */
        var camera_onPreviewFrame: Camera? = null
        /** Camera1 预览回调类的 Class 引用 */
        var camera_callback_calss: Class<*>? = null
        /** 硬件解码器实例，用于将视频解码为帧数据 */
        var hw_decode_obj: VideoToFrames? = null

        /** Camera1 摄像头实例引用 */
        var mcamera1: Camera? = null
        /** Camera1 原始 SurfaceHolder 引用 */
        var oriHolder: SurfaceHolder? = null

    }

    /** Camera2 虚拟 Surface 实例 */
    private var c2_virtual_surface: Surface? = null
    /** Camera2 StateCallback 的 Class 引用 */
    private var c2_state_callback_class: Class<*>? = null
    /** Camera2 StateCallback 实例引用 */
    private var c2_state_callback: CameraDevice.StateCallback? = null

    /**
     * Xposed 模块入口方法，在目标应用加载时被调用。
     *
     * 依次 Hook 以下核心方法：
     * - [Instrumentation.callApplicationOnCreate]：获取目标应用 Context 并初始化播放器
     * - [Camera.setPreviewTexture]：拦截 Camera1 预览纹理设置
     * - [Camera.startPreview]：拦截 Camera1 预览启动
     * - [Camera.setPreviewCallbackWithBuffer]：拦截 Camera1 预览帧回调
     * - [Camera.addCallbackBuffer]：拦截回调缓冲区添加
     * - [Camera.setPreviewDisplay]：拦截 Camera1 预览显示设置
     * - [CameraManager.openCamera]：拦截 Camera2 摄像头打开
     *
     * @param lpparam Xposed 提供的加载包参数，包含目标应用的类加载器和包名
     */
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if(lpparam.packageName == "com.wangyiheng.vcamsx"){
            return
        }
//        if(lpparam.processName.contains(":")) {
//            Log.d(TAG,"当前进程："+lpparam.processName)
//            return
//        }

        //获取context
        XposedHelpers.findAndHookMethod(
            "android.app.Instrumentation", lpparam.classLoader, "callApplicationOnCreate",
            Application::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    param?.args?.firstOrNull()?.let { arg ->
                        if (arg is Application) {
                            val applicationContext = arg.applicationContext
                            if (context != applicationContext) {
                                try {
                                    context = applicationContext
                                    if (!isPlaying) {
                                        isPlaying = true
                                        ijkMediaPlayer ?: initializeTheStateAsWellAsThePlayer()
                                    }
                                } catch (ee: Exception) {
                                    HLog.d(TAG, "$ee")
                                }
                            }
                        }
                    }
                }
            }
        )

        // 支持bilibili摄像头替换
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewTexture",
            SurfaceTexture::class.java, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.d(TAG, "beforeHookedMethod: ")
                    if (param.args[0] == null) {
                        return
                    }
                    if (param.args[0] == fake_SurfaceTexture) {
                        return
                    }
                    if (origin_preview_camera != null && origin_preview_camera == param.thisObject) {
                        param.args[0] = fake_SurfaceTexture
                        return
                    }

                    origin_preview_camera = param.thisObject as Camera
                    original_c1_preview_SurfaceTexture = param.args[0] as SurfaceTexture

                    fake_SurfaceTexture = if (fake_SurfaceTexture == null) {
                        SurfaceTexture(10)
                    } else {
                        fake_SurfaceTexture!!.release()
                        SurfaceTexture(10)
                    }
                    param.args[0] = fake_SurfaceTexture
                }
            })

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "startPreview", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                Log.d(TAG, "beforeHookedMethod: ")
                c1_camera_play()
            }
        })

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewCallbackWithBuffer",
            PreviewCallback::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.d(TAG, "beforeHookedMethod: ")
                    if(videoStatus?.isVideoEnable == false) return
                    if (param.args[0] != null) {
                        process_callback(param)
                    }
                }
            })

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "addCallbackBuffer",
            ByteArray::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.d(TAG, "beforeHookedMethod: ")
                    if (param.args[0] != null) {
                        param.args[0] = ByteArray((param.args[0] as ByteArray).size)
                    }
                }
            })

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewDisplay", SurfaceHolder::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                Log.d(TAG, "beforeHookedMethod: ")
                mcamera1 = param.thisObject as Camera
                oriHolder = param.args[0] as SurfaceHolder
                if (c1FakeTexture == null) {
                    c1FakeTexture = SurfaceTexture(11)
                } else {
                    c1FakeTexture!!.release()
                    c1FakeTexture = SurfaceTexture(11)
                }

                if (c1FakeSurface == null) {
                    c1FakeSurface = Surface(c1FakeTexture)
                } else {
                    c1FakeSurface!!.release()
                    c1FakeSurface = Surface(c1FakeTexture)
                }
                mcamera1!!.setPreviewTexture(c1FakeTexture)
                param.result = null
            }
        })
        XposedHelpers.findAndHookMethod(
            "android.hardware.camera2.CameraManager", lpparam.classLoader, "openCamera",
            String::class.java,
            CameraDevice.StateCallback::class.java,
            Handler::class.java, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        if(param.args[1] == null){
                            return
                        }
                        if(param.args[1] == c2_state_callback){
                            return
                        }
                        c2_state_callback = param.args[1] as CameraDevice.StateCallback
                        c2_state_callback_class = param.args[1]?.javaClass
                        process_camera2_init(c2_state_callback_class as Class<Any>?,lpparam)
                    }catch (e:Exception){
                        HLog.d("android.hardware.camera2.CameraManager报错了", "openCamera")
                    }
                }
            })
    }

    /**
     * 处理 Camera1 预览帧回调的 Hook 逻辑。
     *
     * 拦截 [PreviewCallback.onPreviewFrame] 方法，当摄像头实例匹配时，
     * 使用解码后的视频帧数据 [data_buffer] 替换原始预览数据。
     * 首次调用时会初始化硬件解码器 [VideoToFrames] 并提示用户视频分辨率要求。
     *
     * @param param Xposed 方法 Hook 参数，args[0] 为 PreviewCallback 实例
     */
    private fun process_callback(param: MethodHookParam) {
        val preview_cb_class: Class<*> = param.args[0].javaClass
        XposedHelpers.findAndHookMethod(preview_cb_class, "onPreviewFrame",
            ByteArray::class.java,
            Camera::class.java, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(paramd: MethodHookParam) {
                    val localcam = paramd.args[1] as Camera
                    if (localcam ==  camera_onPreviewFrame) {
                        while ( data_buffer == null) {
                        }
                        System.arraycopy(data_buffer, 0, paramd.args[0], 0, min(data_buffer.size.toDouble(), (paramd.args[0] as ByteArray).size.toDouble()).toInt())
                    } else {
                        camera_callback_calss = preview_cb_class
                        camera_onPreviewFrame = paramd.args[1] as Camera
                        val mwidth = camera_onPreviewFrame!!.getParameters().getPreviewSize().width
                        val mhight = camera_onPreviewFrame!!.getParameters().getPreviewSize().height
                        if ( hw_decode_obj != null) {
                             hw_decode_obj!!.stopDecode()
                        }
                        Toast.makeText(context, """
                                视频需要分辨率与摄像头完全相同
                                宽：${mwidth}
                                高：${mhight}
                                """.trimIndent(), Toast.LENGTH_SHORT).show()
                        hw_decode_obj = VideoToFrames()
                        hw_decode_obj!!.setSaveFrames(OutputImageFormat.NV21)

                        val videoUrl = "content://com.wangyiheng.vcamsx.videoprovider"
                        val videoPathUri = Uri.parse(videoUrl)
                        hw_decode_obj!!.decode( videoPathUri )
                        while ( data_buffer == null) {
                        }
                        System.arraycopy(data_buffer, 0, paramd.args[0], 0, min(data_buffer.size.toDouble(), (paramd.args[0] as ByteArray).size.toDouble()).toInt())
                    }
                }
            })
    }


    /**
     * 处理 Camera2 初始化的 Hook 逻辑。
     *
     * 在 CameraDevice.StateCallback.onOpened 被调用时，创建虚拟 Surface 并
     * Hook createCaptureSession 和 CaptureRequest.Builder.addTarget 方法，
     * 将目标应用的预览 Surface 替换为虚拟 Surface，实现视频注入。
     *
     * @param c2StateCallbackClass Camera2 StateCallback 的 Class 对象
     * @param lpparam Xposed 提供的加载包参数
     */
    private fun process_camera2_init(c2StateCallbackClass: Class<Any>?, lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("beforeHookedMethod method " + c2StateCallbackClass?.simpleName)
        XposedBridge.log("beforeHookedMethod method " + lpparam.classLoader.toString())
        XposedHelpers.findAndHookMethod(c2StateCallbackClass, "onOpened", CameraDevice::class.java, object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                needRecreate = true
                createVirtualSurface()

                c2_reader_Surface = null
                original_preview_Surface = null

                if(lpparam.packageName != "com.ss.android.ugc.aweme" ){
                    XposedHelpers.findAndHookMethod(param.args[0].javaClass, "createCaptureSession", List::class.java, CameraCaptureSession.StateCallback::class.java, Handler::class.java, object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun beforeHookedMethod(paramd: MethodHookParam) {
                            HLog.d("createCaptureSession", "beforeHookedMethod")
                            if (paramd.args[0] != null) {
                                paramd.args[0] = listOf(c2_virtual_surface)
                            }
                        }
                    })
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        XposedHelpers.findAndHookMethod(param.args[0].javaClass, "createCaptureSession",
                            SessionConfiguration::class.java, object : XC_MethodHook() {
                                @Throws(Throwable::class)
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    super.beforeHookedMethod(param)
                                    if (param.args[0] != null) {
                                        sessionConfiguration = param.args[0] as SessionConfiguration
                                        outputConfiguration = OutputConfiguration(c2_virtual_surface!!)
                                        fake_sessionConfiguration = SessionConfiguration(
                                            sessionConfiguration!!.sessionType,
                                            Arrays.asList<OutputConfiguration>(outputConfiguration),
                                            sessionConfiguration!!.getExecutor(),
                                            sessionConfiguration!!.getStateCallback()
                                        )
                                        param.args[0] = fake_sessionConfiguration
                                    }
                                }
                            })
                    }
                }
            }
        })


        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder",
            lpparam.classLoader,
            "addTarget",
            android.view.Surface::class.java, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    HLog.d(msg = "android.hardware.camera2.CaptureRequest.Builder addTarget")
                    if (param.args[0] != null) {
                        if(param.args[0] == c2_virtual_surface)return
                        val surfaceInfo = param.args[0].toString()
                        if (!surfaceInfo.contains("Surface(name=null)")) {
                            if(original_preview_Surface != param.args[0] as Surface ){
                                original_preview_Surface = param.args[0] as Surface
                            }
                        }else{
                            if(c2_reader_Surface == null && lpparam.packageName != "com.ss.android.ugc.aweme"){
                                c2_reader_Surface = param.args[0] as Surface
                            }
                        }
                        if(lpparam.packageName != "com.ss.android.ugc.aweme"){
                            param.args[0] = c2_virtual_surface
                        }
                    }
                }
            })

        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder",
            lpparam.classLoader,
            "build",object :XC_MethodHook(){
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                HLog.d(msg = "android.hardware.camera2.CaptureRequest.Builder build")
                camera2Play()
            }
        })
    }

    /**
     * 创建或重建 Camera2 虚拟 Surface。
     *
     * 当 [needRecreate] 为 true 时，释放旧的 SurfaceTexture 和 Surface 并重新创建；
     * 当虚拟 Surface 为 null 时，递归调用自身触发重建。
     *
     * @return 创建或复用的 Camera2 虚拟 [Surface] 实例，可能为 null
     */
    private fun createVirtualSurface(): Surface? {
        HLog.d(msg = "createVirtualSurface")
        if (needRecreate) {
            c2VirtualSurfaceTexture?.release()
            c2VirtualSurfaceTexture = null

            c2_virtual_surface?.release()
            c2_virtual_surface = null

            c2VirtualSurfaceTexture = SurfaceTexture(15)
            c2_virtual_surface = Surface(c2VirtualSurfaceTexture)
            needRecreate = false
        } else if (c2_virtual_surface == null) {
            needRecreate = true
            c2_virtual_surface = createVirtualSurface()
        }
        return c2_virtual_surface
    }
}

