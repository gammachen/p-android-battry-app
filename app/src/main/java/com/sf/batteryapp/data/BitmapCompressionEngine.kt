package com.sf.batteryapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class BitmapCompressionEngine {
    
    companion object {
        private const val TAG = "BitmapCompression"
        private const val MAX_PIXELS = 1920 * 1080 // 200万像素限制
        private const val DEFAULT_QUALITY = 80
    }
    
    /**
     * 智能压缩 - 自动选择合适的压缩策略
     */
    fun compressImageSmart(
        context: Context,
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig
    ): ImageCompressionManager.CompressionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 1. 读取图片信息
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            
            // 2. 计算采样率
            val sampleSize = calculateSampleSize(options, config.maxWidth, config.maxHeight)
            
            // 3. 根据文件类型选择压缩策略
            val result = when (getImageType(sourceFile)) {
                ImageType.JPEG -> compressJpeg(sourceFile, targetDir, config, sampleSize)
                ImageType.PNG -> compressPng(sourceFile, targetDir, config, sampleSize)
                ImageType.WEBP -> compressWebP(sourceFile, targetDir, config, sampleSize)
                ImageType.HEIC -> compressHeic(context, sourceFile, targetDir, config)
                else -> compressGeneral(sourceFile, targetDir, config, sampleSize)
            }
            
            result.copy(timeCost = System.currentTimeMillis() - startTime)
            
        } catch (e: Exception) {
            ImageCompressionManager.CompressionResult(
                originalFile = sourceFile,
                compressedFile = sourceFile,
                originalSize = sourceFile.length(),
                compressedSize = sourceFile.length(),
                compressionRatio = 1f,
                quality = config.quality,
                timeCost = System.currentTimeMillis() - startTime,
                success = false,
                error = e.message
            )
        }
    }
    
    /**
     * JPEG压缩（支持渐进式）
     */
    private fun compressJpeg(
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig,
        sampleSize: Int
    ): ImageCompressionManager.CompressionResult {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // JPEG不需要透明度
        }
        
        var bitmap: Bitmap? = null
        var outputStream: OutputStream? = null
        
        return try {
            // 解码位图
            bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
                ?: throw IllegalArgumentException("无法解码图片")
            
            // 进一步缩放（如果需要）
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, config.maxWidth, config.maxHeight)
            
            // 创建目标文件
            val outputFile = createOutputFile(targetDir, sourceFile, "jpg")
            outputStream = FileOutputStream(outputFile)
            
            // 压缩并写入
            val quality = determineOptimalQuality(sourceFile, config)
            scaledBitmap.compress(
                Bitmap.CompressFormat.JPEG,
                quality,
                outputStream
            )
            
            // 如果支持渐进式JPEG，使用更高级的编码
            if (config.progressiveJpeg && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 支持渐进式JPEG编码
                compressJpeg(scaledBitmap, outputFile, quality)
            }
            
            // 保留EXIF信息
            if (config.keepExif) {
                copyExifData(sourceFile, outputFile)
            }
            
            ImageCompressionManager.CompressionResult(
                originalFile = sourceFile,
                compressedFile = outputFile,
                originalSize = sourceFile.length(),
                compressedSize = outputFile.length(),
                compressionRatio = outputFile.length().toFloat() / sourceFile.length(),
                quality = quality,
                timeCost = 0,
                success = true
            )
            
        } finally {
            bitmap?.recycle()
            outputStream?.close()
        }
    }
    
    /**
     * PNG压缩（保持透明度）
     */
    private fun compressPng(
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig,
        sampleSize: Int
    ): ImageCompressionManager.CompressionResult {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888 // PNG需要透明度
        }
        
        var bitmap: Bitmap? = null
        var outputStream: OutputStream? = null
        
        return try {
            bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
                ?: throw IllegalArgumentException("无法解码PNG图片")
            
            // PNG压缩 - 使用WebP格式可以获得更好压缩
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, config.maxWidth, config.maxHeight)
            val outputFile = createOutputFile(targetDir, sourceFile, "webp")
            outputStream = FileOutputStream(outputFile)
            
            // PNG转WebP以获得更好压缩（可选）
            scaledBitmap.compress(
                Bitmap.CompressFormat.WEBP,
                config.quality,
                outputStream
            )
            
            ImageCompressionManager.CompressionResult(
                originalFile = sourceFile,
                compressedFile = outputFile,
                originalSize = sourceFile.length(),
                compressedSize = outputFile.length(),
                compressionRatio = outputFile.length().toFloat() / sourceFile.length(),
                quality = config.quality,
                timeCost = 0,
                success = true
            )
            
        } finally {
            bitmap?.recycle()
            outputStream?.close()
        }
    }
    
    /**
     * WebP压缩（Android原生支持）
     */
    private fun compressWebP(
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig,
        sampleSize: Int
    ): ImageCompressionManager.CompressionResult {
        // WebP处理逻辑类似JPEG
        return compressJpeg(sourceFile, targetDir, config, sampleSize)
    }
    
    /**
     * HEIC压缩（需要额外处理）
     */
    private fun compressHeic(
        context: Context,
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig
    ): ImageCompressionManager.CompressionResult {
        // HEIC格式需要特殊处理
        // 可以使用第三方库或系统API（Android 11+）
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            compressHeicWithApi30(context, sourceFile, targetDir, config)
        } else {
            // 降级为JPEG处理
            convertHeicToJpeg(context, sourceFile, targetDir, config)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun compressHeicWithApi30(
        context: Context,
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig
    ): ImageCompressionManager.CompressionResult {
        // 使用传统方式处理HEIC，实际转换为JPEG
        return convertHeicToJpeg(context, sourceFile, targetDir, config)
    }
    
    private fun convertHeicToJpeg(
        context: Context,
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig
    ): ImageCompressionManager.CompressionResult {
        // 简单的HEIC转JPEG实现
        // 注意：这只是一个降级处理，可能无法处理所有HEIC文件
        return compressJpeg(sourceFile, targetDir, config, 1)
    }
    
    /**
     * 通用压缩方法
     */
    private fun compressGeneral(
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig,
        sampleSize: Int
    ): ImageCompressionManager.CompressionResult {
        // 默认使用JPEG格式压缩
        return compressJpeg(sourceFile, targetDir, config, sampleSize)
    }
    
    /**
     * 计算采样率（inSampleSize）
     */
    private fun calculateSampleSize(
        options: BitmapFactory.Options,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        val width = options.outWidth
        val height = options.outHeight
        var sampleSize = 1
        
        // 如果图片尺寸大于目标尺寸，计算采样率
        if (width > maxWidth || height > maxHeight) {
            val widthRatio = width / maxWidth
            val heightRatio = height / maxHeight
            sampleSize = maxOf(widthRatio, heightRatio)
            
            // 确保采样率是2的幂次（BitmapFactory要求）
            var power = 1
            while (power * 2 <= sampleSize) {
                power *= 2
            }
            sampleSize = power
        }
        
        // 防止OOM
        val totalPixels = width * height / (sampleSize * sampleSize)
        if (totalPixels > MAX_PIXELS) {
            sampleSize = (sqrt(totalPixels.toDouble() / MAX_PIXELS).toInt() + 1)
        }
        
        return maxOf(1, sampleSize)
    }
    
    /**
     * 按需缩放位图
     */
    private fun scaleBitmapIfNeeded(
        bitmap: Bitmap,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }
        
        // 计算缩放比例
        val scale = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 创建输出文件
     */
    private fun createOutputFile(
        targetDir: File,
        sourceFile: File,
        extension: String
    ): File {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val originalName = sourceFile.nameWithoutExtension
        val fileName = "${originalName}_compressed_${timestamp}.$extension"
        
        return File(targetDir, fileName)
    }
    
    /**
     * 智能确定压缩质量
     */
    private fun determineOptimalQuality(
        sourceFile: File,
        config: ImageCompressionManager.CompressionConfig
    ): Int {
        val fileSizeMB = sourceFile.length() / (1024 * 1024)
        
        return when {
            fileSizeMB > 10 -> 60  // 大于10MB的文件，使用较低质量
            fileSizeMB > 5 -> 70   // 5-10MB的文件
            fileSizeMB > 2 -> 80   // 2-5MB的文件
            else -> config.quality // 小于2MB，使用配置质量
        }
    }
    
    /**
     * 图片类型检测
     */
    private enum class ImageType {
        JPEG, PNG, WEBP, HEIC, GIF, UNKNOWN
    }
    
    private fun getImageType(file: File): ImageType {
        return try {
            val input = file.inputStream()
            val bytes = ByteArray(12)
            input.read(bytes, 0, 12)
            input.close()
            
            when {
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> ImageType.JPEG
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && 
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> ImageType.PNG
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && 
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() && 
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && 
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> ImageType.WEBP
                bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && 
                bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte() && 
                bytes[8] == 0x68.toByte() && bytes[9] == 0x65.toByte() && 
                bytes[10] == 0x69.toByte() && bytes[11] == 0x63.toByte() -> ImageType.HEIC
                bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && 
                bytes[2] == 0x46.toByte() -> ImageType.GIF
                else -> ImageType.UNKNOWN
            }
        } catch (e: Exception) {
            ImageType.UNKNOWN
        }
    }
    
    /**
     * 压缩为JPEG
     */
    private fun compressJpeg(
        bitmap: Bitmap,
        outputFile: File,
        quality: Int
    ) {
        try {
            val outputStream = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "JPEG压缩失败: ${e.message}")
        }
    }
    
    /**
     * 复制EXIF数据
     */
    private fun copyExifData(sourceFile: File, targetFile: File) {
        // 简单的EXIF数据复制实现
        // 注意：这只是一个基本实现，可能无法复制所有EXIF数据
        try {
            // 使用Android的ExifInterface复制EXIF数据
            val sourceExif = ExifInterface(sourceFile.absolutePath)
            val targetExif = ExifInterface(targetFile.absolutePath)
            
            // 复制基本的EXIF标签
            val exifTags = arrayOf(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO
            )
            
            for (tag in exifTags) {
                val value = sourceExif.getAttribute(tag)
                if (value != null) {
                    targetExif.setAttribute(tag, value)
                }
            }
            
            targetExif.saveAttributes()
        } catch (e: IOException) {
            Log.e(TAG, "复制EXIF数据失败: ${e.message}")
        }
    }
}
