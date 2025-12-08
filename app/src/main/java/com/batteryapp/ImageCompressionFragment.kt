package com.batteryapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.batteryapp.data.BitmapCompressionEngine
import com.batteryapp.data.ImageCompressionManager
import java.io.File

class ImageCompressionFragment : Fragment() {

    private lateinit var radioGroupCompressionMode: RadioGroup
    private lateinit var btnSelectDirectory: Button
    private lateinit var tvSelectedDirectory: TextView
    private lateinit var btnStartCompression: Button
    private lateinit var layoutCompressionResult: LinearLayout
    private lateinit var tvCompressionResult: TextView

    private var selectedDirectoryUri: Uri? = null
    private var compressionMode: CompressionMode = CompressionMode.KEEP_ORIGINAL

    enum class CompressionMode {
        KEEP_ORIGINAL,
        DELETE_ORIGINAL
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_image_compression, container, false)

        // 初始化UI组件
        radioGroupCompressionMode = view.findViewById(R.id.radio_group_compression_mode)
        btnSelectDirectory = view.findViewById(R.id.btn_select_directory)
        tvSelectedDirectory = view.findViewById(R.id.tv_selected_directory)
        btnStartCompression = view.findViewById(R.id.btn_start_compression)
        layoutCompressionResult = view.findViewById(R.id.layout_compression_result)
        tvCompressionResult = view.findViewById(R.id.tv_compression_result)

        // 设置压缩模式选择监听
        radioGroupCompressionMode.setOnCheckedChangeListener { _, checkedId ->
            compressionMode = when (checkedId) {
                R.id.radio_keep_original -> CompressionMode.KEEP_ORIGINAL
                R.id.radio_delete_original -> CompressionMode.DELETE_ORIGINAL
                else -> CompressionMode.KEEP_ORIGINAL
            }
        }

        // 设置目录选择按钮监听
        btnSelectDirectory.setOnClickListener {
            selectDirectory()
        }

        // 设置开始压缩按钮监听
        btnStartCompression.setOnClickListener {
            startCompression()
        }

        return view
    }

    /**
     * 选择目录
     */
    private fun selectDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        startActivityForResult(intent, REQUEST_CODE_SELECT_DIRECTORY)
    }

    /**
     * 处理目录选择结果
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_SELECT_DIRECTORY && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedDirectoryUri = uri
                // 保存权限
                activity?.contentResolver?.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                // 更新UI
                tvSelectedDirectory.text = "已选择目录: ${uri.path}"
            }
        }
    }

    /**
     * 开始压缩
     */
    private fun startCompression() {
        if (selectedDirectoryUri == null) {
            Toast.makeText(context, "请先选择目录", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示压缩进度
        Toast.makeText(context, "开始压缩...", Toast.LENGTH_SHORT).show()

        // 启动压缩任务
        Thread {
            try {
                // 获取目录中的所有图片文件
                val imageFiles = getImageFilesFromDirectory(selectedDirectoryUri!!)
                
                if (imageFiles.isEmpty()) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "目录中没有图片文件", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                // 初始化压缩引擎
                val compressionEngine = BitmapCompressionEngine()
                val config = ImageCompressionManager.CompressionConfig(
                    quality = 80,
                    maxWidth = 1920,
                    maxHeight = 1080
                )

                // 压缩结果统计
                var totalOriginalSize = 0L
                var totalCompressedSize = 0L
                var successCount = 0
                var errorCount = 0

                // 根据压缩模式选择目标目录
                imageFiles.forEach { sourceFile ->
                    try {
                        val targetDir = when (compressionMode) {
                            CompressionMode.KEEP_ORIGINAL -> {
                                // 保留源文件模式：创建子目录存储压缩文件
                                val compressedSubDir = File(sourceFile.parent, "compressed")
                                if (!compressedSubDir.exists()) {
                                    compressedSubDir.mkdirs()
                                }
                                compressedSubDir
                            }
                            CompressionMode.DELETE_ORIGINAL -> {
                                // 删除源文件模式：直接压缩到当前目录
                                sourceFile.parentFile!!
                            }
                        }

                        // 执行压缩
                        val result = compressionEngine.compressImageSmart(
                            requireContext(),
                            sourceFile,
                            targetDir,
                            config
                        )

                        // 更新统计
                        totalOriginalSize += result.originalSize
                        totalCompressedSize += result.compressedSize
                        
                        if (result.success) {
                            successCount++
                            // 如果是删除源文件模式，删除原始文件
                            if (compressionMode == CompressionMode.DELETE_ORIGINAL && 
                                result.compressedFile != result.originalFile) {
                                result.originalFile.delete()
                            }
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "压缩文件失败: ${sourceFile.name}, ${e.message}")
                    }
                }

                // 计算统计结果
                val totalSavedSize = totalOriginalSize - totalCompressedSize
                val averageCompressionRatio = if (totalOriginalSize > 0) {
                    (1 - totalCompressedSize.toFloat() / totalOriginalSize) * 100
                } else {
                    0f
                }

                // 构建结果消息
                val resultMessage = "压缩完成！\n\n" +
                        "总文件数: ${imageFiles.size}\n" +
                        "成功: $successCount\n" +
                        "失败: $errorCount\n" +
                        "原始大小: ${formatFileSize(totalOriginalSize)}\n" +
                        "压缩后大小: ${formatFileSize(totalCompressedSize)}\n" +
                        "节省空间: ${formatFileSize(totalSavedSize)}\n" +
                        "平均压缩率: ${String.format("%.1f", averageCompressionRatio)}%"

                // 更新UI
                activity?.runOnUiThread {
                    showCompressionResult(resultMessage)
                    Toast.makeText(context, "压缩完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "压缩过程中发生错误: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(context, "压缩失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * 从目录Uri获取所有图片文件
     */
    private fun getImageFilesFromDirectory(directoryUri: Uri): List<File> {
        val imageFiles = mutableListOf<File>()
        
        try {
            // 注意：这里需要处理Uri到File的转换
            // 由于Android 10+的存储权限限制，直接从Uri获取File可能不可行
            // 这里使用一个简化的实现，假设Uri指向的是本地文件系统路径
            
            // 获取目录路径
            val directoryPath = directoryUri.path?.let {
                // 处理不同的Uri格式
                if (it.startsWith("/tree/primary:", ignoreCase = true)) {
                    // 处理DocumentsProvider Uri
                    val relativePath = it.substringAfter("/tree/primary:")
                    "${android.os.Environment.getExternalStorageDirectory()}/$relativePath"
                } else {
                    // 直接使用路径
                    it
                }
            }

            if (directoryPath != null) {
                val directory = File(directoryPath)
                if (directory.exists() && directory.isDirectory) {
                    // 遍历目录中的所有文件
                    directory.listFiles()?.forEach { file ->
                        if (file.isFile && isImageFile(file)) {
                            imageFiles.add(file)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取图片文件失败: ${e.message}")
        }
        
        return imageFiles
    }

    /**
     * 判断是否为图片文件
     */
    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.toLowerCase()
        return listOf("jpg", "jpeg", "png", "webp", "heic", "gif").contains(extension)
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024f)} KB"
            bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024f * 1024f))} MB"
            else -> "${String.format("%.1f", bytes / (1024f * 1024f * 1024f))} GB"
        }
    }

    companion object {
        private const val TAG = "ImageCompression"
        private const val REQUEST_CODE_SELECT_DIRECTORY = 1001
    }

    /**
     * 显示压缩结果
     */
    private fun showCompressionResult(result: String) {
        tvCompressionResult.text = result
        layoutCompressionResult.visibility = View.VISIBLE
    }
}
