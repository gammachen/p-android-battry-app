package com.sf.batteryapp.data

import android.graphics.Bitmap
import android.os.Build
import java.io.File

object ImageCompressionManager {
    
    enum class QualityLevel(val value: Int) {
        LOW(30),        // 低质量 - 最大压缩
        MEDIUM(70),     // 中等质量 - 平衡压缩
        HIGH(85),       // 高质量 - 最小压缩
        CUSTOM(-1)      // 自定义质量
    }
    
    data class CompressionConfig(
        val maxWidth: Int = 1920,          // 最大宽度
        val maxHeight: Int = 1080,         // 最大高度
        val quality: Int = 80,             // 压缩质量 (0-100)
        val format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        val maxFileSizeKB: Long = 1024,    // 目标文件大小 (KB)
        val keepExif: Boolean = true,      // 是否保留EXIF信息
        val progressiveJpeg: Boolean = false // 是否使用渐进式JPEG
    )
    
    data class CompressionResult(
        val originalFile: File,
        val compressedFile: File,
        val originalSize: Long,
        val compressedSize: Long,
        val compressionRatio: Float,  // 压缩比例
        val quality: Int,
        val timeCost: Long,           // 耗时(ms)
        val success: Boolean,
        val error: String? = null
    ) {
        val savedBytes: Long get() = originalSize - compressedSize
        val savedPercentage: Float get() = (1 - compressedSize.toFloat() / originalSize) * 100
    }
}
