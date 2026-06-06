package com.wangyiheng.vcamsx.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.wangyiheng.vcamsx.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * 视频文件内容提供器，为 Xposed Hook 进程提供视频文件访问能力。
 *
 * 通过 ContentProvider 机制，使被 Hook 的应用能够通过
 * "content://com.wangyiheng.vcamsx.videoprovider" URI 访问用户选择的视频文件。
 * 使用 Koin 注入 [InfoManager] 获取视频文件路径配置。
 */
class VideoProvider : ContentProvider(), KoinComponent {
    /** 信息管理器，用于读取视频文件路径配置 */
    val infoManager by inject<InfoManager>()

//    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
//        val readerContent = extractContent(uri.toString())
//        Log.d("vcamsx", "内容"+readerContent)
//        // 获取外部文件目录
//        val externalFilesDir = context?.getExternalFilesDir(null)?.absolutePath ?: return null
//
//        // 创建一个指向 "copied_video.mp4" 的文件对象
//        val vcamsxFile = File(externalFilesDir, "copied_video.mp4")
//
//        // 检查文件是否存在，如果不存在，则从资源中复制
//        if (!vcamsxFile.exists()) {
//            try {
//                // 使用 try-with-resources 语句确保资源被正确关闭
//                context?.resources?.openRawResource(R.raw.vcamsx)?.use { inputStream ->
//                    FileOutputStream(vcamsxFile).use { fileOutputStream ->
//                        inputStream.copyTo(fileOutputStream)
//                    }
//                }
//            } catch (e: IOException) {
//                e.printStackTrace()
//                return null
//            }
//        }
//        // 返回文件的 ParcelFileDescriptor，设置为只读模式
//        return ParcelFileDescriptor.open(vcamsxFile, ParcelFileDescriptor.MODE_READ_ONLY)
//    }

    /**
     * 打开视频文件，返回文件描述符。
     *
     * 从 [InfoManager] 读取视频 URI，通过 ContentResolver 打开对应文件。
     *
     * @param uri 请求的内容 URI
     * @param mode 文件打开模式（如 "r" 只读）
     * @return 视频文件的 [ParcelFileDescriptor]，打开失败返回 null
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val videoInfo = infoManager.getVideoInfo()
        val url = videoInfo!!.videoUrl
        val fixedUri = Uri.parse(url)

        return try {
            // 直接使用ContentResolver打开固定URI指向的文件的ParcelFileDescriptor
            context!!.contentResolver.openFileDescriptor(fixedUri, mode)
        } catch (e: Exception) { // 捕获所有异常，包括FileNotFoundException
            Log.e("Error", "打开文件失败: ${e.message}")
            null
        }
    }


    /**
     * 内容提供器初始化。
     *
     * @return 始终返回 true
     */
    override fun onCreate(): Boolean {
        // 初始化内容提供器
        return true
    }

    /**
     * 从 URL 中提取 provider 路径之后的内容部分。
     *
     * @param url 完整的 content URI 字符串
     * @return 提取的内容字符串，未匹配到前缀时返回空字符串
     */
    fun extractContent(url: String): String {
        val prefix = "com.wangyiheng.vcamsx.videoprovider/"
        val index = url.indexOf(prefix)

        return if (index != -1) {
            url.substring(index + prefix.length)
        } else {
            ""
        }
    }

    /**
     * 查询视频文件信息，返回包含文件名、大小、修改时间的 Cursor。
     *
     * @param uri 请求的内容 URI
     * @param projection 需要返回的列名数组
     * @param selection 过滤条件
     * @param selectionArgs 过滤条件参数
     * @param sortOrder 排序方式
     * @return 包含视频文件信息的 [MatrixCursor]
     */
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        // 创建MatrixCursor
        val cursor = MatrixCursor(arrayOf("_id", "display_name", "size", "date_modified","file"))
        val path = context?.getExternalFilesDir(null)!!.absolutePath
        val file = File(path, "advancedModeMovies/654e1835b70883406c4640c3/caibi_60.mp4")
        // 获取视频文件夹路径
        cursor.addRow(arrayOf(0, file.name, file.length(), file.lastModified(),file))

        return cursor
    }

    // 其他方法根据需要实现，这里为了简单起见，我们留空
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
