# Android 图片压缩实施方案

## **一、整体架构设计**

```kotlin
// 图片压缩管理器
object ImageCompressionManager {
    
    // 压缩质量级别
    enum class QualityLevel(val value: Int) {
        LOW(30),        // 低质量 - 最大压缩
        MEDIUM(70),     // 中等质量 - 平衡压缩
        HIGH(85),       // 高质量 - 最小压缩
        CUSTOM(-1)      // 自定义质量
    }
    
    // 压缩配置
    data class CompressionConfig(
        val maxWidth: Int = 1920,          // 最大宽度
        val maxHeight: Int = 1080,         // 最大高度
        val quality: Int = 80,             // 压缩质量 (0-100)
        val format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        val maxFileSizeKB: Long = 1024,    // 目标文件大小 (KB)
        val keepExif: Boolean = true,      // 是否保留EXIF信息
        val progressiveJpeg: Boolean = false // 是否使用渐进式JPEG
    )
    
    // 压缩结果
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
```

## **二、核心压缩引擎实现**

### **方案1：基于Bitmap的压缩（基础方案）**

```kotlin
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
                compressProgressiveJpeg(scaledBitmap, outputFile, quality)
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
    
    @RequiresApi(Build.VERSION_CODES.R)
    private fun compressHeicWithApi30(
        context: Context,
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig
    ): ImageCompressionManager.CompressionResult {
        // 使用Android 11的HEIF/HEVC编码器
        val outputFile = createOutputFile(targetDir, sourceFile, "heic")
        
        return try {
            val source = ImageDecoder.createSource(sourceFile)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // 配置解码器
                decoder.setTargetSize(config.maxWidth, config.maxHeight)
            }
            
            val outputStream = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.WEBP, config.quality, outputStream)
            outputStream.close()
            bitmap.recycle()
            
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
        } catch (e: Exception) {
            // 失败时降级处理
            convertHeicToJpeg(context, sourceFile, targetDir, config)
        }
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
            val input = FileInputStream(file)
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
}
```

### **方案2：使用Libjpeg-turbo（高性能方案）**

```kotlin
// 使用第三方库进行高性能JPEG压缩
object LibjpegCompressionEngine {
    
    init {
        // 加载native库
        System.loadLibrary("jpeg-turbo")
        System.loadLibrary("imagecompression")
    }
    
    external fun compressJpegTurbo(
        inputPath: String,
        outputPath: String,
        quality: Int,
        progressive: Boolean,
        optimizeHuffman: Boolean
    ): CompressionResult
    
    external fun getLibVersion(): String
    
    /**
     * 使用Libjpeg-turbo进行高质量压缩
     */
    fun compressWithLibjpeg(
        sourceFile: File,
        targetFile: File,
        quality: Int = 85
    ): ImageCompressionManager.CompressionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = compressJpegTurbo(
                inputPath = sourceFile.absolutePath,
                outputPath = targetFile.absolutePath,
                quality = quality,
                progressive = true,  // 使用渐进式JPEG
                optimizeHuffman = true // 优化霍夫曼编码
            )
            
            ImageCompressionManager.CompressionResult(
                originalFile = sourceFile,
                compressedFile = targetFile,
                originalSize = sourceFile.length(),
                compressedSize = targetFile.length(),
                compressionRatio = targetFile.length().toFloat() / sourceFile.length(),
                quality = quality,
                timeCost = System.currentTimeMillis() - startTime,
                success = true
            )
            
        } catch (e: Exception) {
            ImageCompressionManager.CompressionResult(
                originalFile = sourceFile,
                compressedFile = sourceFile,
                originalSize = sourceFile.length(),
                compressedSize = sourceFile.length(),
                compressionRatio = 1f,
                quality = quality,
                timeCost = System.currentTimeMillis() - startTime,
                success = false,
                error = e.message
            )
        }
    }
    
    data class CompressionResult(
        val success: Boolean,
        val originalSize: Long,
        val compressedSize: Long,
        val errorCode: Int
    )
}
```

### **方案3：使用Android的ImageDecoder（Android P+）**

```kotlin
@RequiresApi(Build.VERSION_CODES.P)
object ModernImageCompressionEngine {
    
    /**
     * 使用ImageDecoder API（Android 9.0+）
     * 支持HEIF、WebP等现代格式
     */
    fun compressWithImageDecoder(
        context: Context,
        sourceFile: File,
        targetFile: File,
        config: ImageCompressionManager.CompressionConfig
    ): ImageCompressionManager.CompressionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val source = ImageDecoder.createSource(sourceFile)
            
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, source ->
                // 设置目标尺寸
                decoder.setTargetSize(config.maxWidth, config.maxHeight)
                
                // 配置解码选项
                decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM)
                
                // 如果是JPEG，可以配置渐进式解码
                if (info.mimeType == "image/jpeg") {
                    decoder.isDecodeAsAlphaMaskEnabled = false
                }
                
                // 保留EXIF
                if (config.keepExif) {
                    decoder.setData(source)
                }
            }
            
            // 压缩并保存
            val outputStream = FileOutputStream(targetFile)
            bitmap.compress(config.format, config.quality, outputStream)
            outputStream.close()
            bitmap.recycle()
            
            ImageCompressionManager.CompressionResult(
                originalFile = sourceFile,
                compressedFile = targetFile,
                originalSize = sourceFile.length(),
                compressedSize = targetFile.length(),
                compressionRatio = targetFile.length().toFloat() / sourceFile.length(),
                quality = config.quality,
                timeCost = System.currentTimeMillis() - startTime,
                success = true
            )
            
        } catch (e: Exception) {
            // 降级到传统方法
            BitmapCompressionEngine().compressImageSmart(
                context, sourceFile, targetFile.parentFile!!, config
            )
        }
    }
}
```

## **三、批量压缩管理器**

```kotlin
class BatchCompressionManager {
    
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val handler = Handler(Looper.getMainLooper())
    
    interface CompressionCallback {
        fun onStart(total: Int)
        fun onProgress(current: Int, total: Int, result: ImageCompressionManager.CompressionResult)
        fun onComplete(results: List<ImageCompressionManager.CompressionResult>)
        fun onError(error: String)
    }
    
    /**
     * 批量压缩目录中的图片
     */
    fun compressDirectory(
        context: Context,
        sourceDir: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig,
        callback: CompressionCallback
    ): CancellableTask {
        val task = CancellableTask()
        
        executor.execute {
            try {
                // 扫描目录中的图片文件
                val imageFiles = scanImageFiles(sourceDir)
                val total = imageFiles.size
                
                handler.post { callback.onStart(total) }
                
                val results = mutableListOf<ImageCompressionManager.CompressionResult>()
                
                imageFiles.forEachIndexed { index, file ->
                    if (task.isCancelled) return@execute
                    
                    try {
                        // 检查文件是否需要压缩
                        if (shouldCompress(file, config.maxFileSizeKB)) {
                            val result = compressSingleFile(context, file, targetDir, config)
                            results.add(result)
                            
                            handler.post {
                                callback.onProgress(index + 1, total, result)
                            }
                        } else {
                            // 跳过，直接复制
                            val copiedFile = copyFile(file, targetDir)
                            val result = ImageCompressionManager.CompressionResult(
                                originalFile = file,
                                compressedFile = copiedFile,
                                originalSize = file.length(),
                                compressedSize = copiedFile.length(),
                                compressionRatio = 1f,
                                quality = config.quality,
                                timeCost = 0,
                                success = true
                            )
                            results.add(result)
                        }
                    } catch (e: Exception) {
                        val errorResult = ImageCompressionManager.CompressionResult(
                            originalFile = file,
                            compressedFile = file,
                            originalSize = file.length(),
                            compressedSize = file.length(),
                            compressionRatio = 1f,
                            quality = config.quality,
                            timeCost = 0,
                            success = false,
                            error = e.message
                        )
                        results.add(errorResult)
                    }
                }
                
                handler.post { callback.onComplete(results) }
                
            } catch (e: Exception) {
                handler.post { callback.onError(e.message ?: "批量压缩失败") }
            }
        }
        
        return task
    }
    
    /**
     * 扫描图片文件
     */
    private fun scanImageFiles(directory: File): List<File> {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "bmp", "gif")
        
        return directory.listFiles()?.filter { file ->
            file.isFile && imageExtensions.any { ext ->
                file.name.lowercase(Locale.getDefault()).endsWith(".$ext")
            }
        }?.sortedBy { it.name } ?: emptyList()
    }
    
    /**
     * 判断是否需要压缩
     */
    private fun shouldCompress(file: File, maxSizeKB: Long): Boolean {
        val fileSizeKB = file.length() / 1024
        return fileSizeKB > maxSizeKB
    }
    
    /**
     * 压缩单个文件
     */
    private fun compressSingleFile(
        context: Context,
        sourceFile: File,
        targetDir: File,
        config: ImageCompressionManager.CompressionConfig
    ): ImageCompressionManager.CompressionResult {
        // 根据Android版本选择合适的引擎
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val targetFile = createTargetFile(sourceFile, targetDir)
            ModernImageCompressionEngine.compressWithImageDecoder(
                context, sourceFile, targetFile, config
            )
        } else {
            BitmapCompressionEngine().compressImageSmart(
                context, sourceFile, targetDir, config
            )
        }
    }
    
    /**
     * 创建目标文件
     */
    private fun createTargetFile(sourceFile: File, targetDir: File): File {
        val extension = sourceFile.extension
        val name = sourceFile.nameWithoutExtension
        val timestamp = System.currentTimeMillis()
        
        return File(targetDir, "${name}_compressed_${timestamp}.$extension")
    }
    
    /**
     * 复制文件（跳过压缩）
     */
    private fun copyFile(source: File, targetDir: File): File {
        val targetFile = File(targetDir, source.name)
        source.copyTo(targetFile, overwrite = true)
        return targetFile
    }
    
    /**
     * 可取消的任务
     */
    class CancellableTask {
        @Volatile
        private var cancelled = false
        
        fun cancel() {
            cancelled = true
        }
        
        fun isCancelled(): Boolean = cancelled
    }
}
```

## **四、智能压缩策略**

```kotlin
object SmartCompressionStrategy {
    
    /**
     * 根据图片内容智能选择压缩参数
     */
    data class SmartCompressionParams(
        val quality: Int,
        val maxWidth: Int,
        val maxHeight: Int,
        val format: Bitmap.CompressFormat,
        val strategy: String
    )
    
    /**
     * 分析图片并生成智能压缩参数
     */
    fun analyzeImageForCompression(
        context: Context,
        imageFile: File
    ): SmartCompressionParams {
        // 1. 获取图片基本信息
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)
        
        val width = options.outWidth
        val height = options.outHeight
        val mimeType = options.outMimeType
        val fileSizeKB = imageFile.length() / 1024
        
        // 2. 分析图片类型
        val isPhotograph = detectIfPhotograph(imageFile)
        val hasAlpha = mimeType == "image/png" || mimeType == "image/webp"
        val isScreenshot = detectIfScreenshot(imageFile.name, width, height)
        
        // 3. 根据分析结果生成参数
        return when {
            // 照片 - 可以较高压缩
            isPhotograph -> SmartCompressionParams(
                quality = 75,
                maxWidth = if (width > 1920) 1920 else width,
                maxHeight = if (height > 1080) 1080 else height,
                format = Bitmap.CompressFormat.JPEG,
                strategy = "photograph"
            )
            
            // 截图 - 保持清晰度
            isScreenshot -> SmartCompressionParams(
                quality = 90,
                maxWidth = width,  // 保持原尺寸
                maxHeight = height,
                format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                strategy = "screenshot"
            )
            
            // 包含透明度的图片
            hasAlpha -> SmartCompressionParams(
                quality = 85,
                maxWidth = if (width > 1920) 1920 else width,
                maxHeight = if (height > 1080) 1080 else height,
                format = Bitmap.CompressFormat.WEBP,  // WebP支持透明度且压缩比高
                strategy = "alpha_image"
            )
            
            // 特大文件
            fileSizeKB > 5120 -> SmartCompressionParams(
                quality = 60,
                maxWidth = 1280,
                maxHeight = 720,
                format = Bitmap.CompressFormat.JPEG,
                strategy = "large_file"
            )
            
            // 默认策略
            else -> SmartCompressionParams(
                quality = 80,
                maxWidth = if (width > 1920) 1920 else width,
                maxHeight = if (height > 1080) 1080 else height,
                format = Bitmap.CompressFormat.JPEG,
                strategy = "default"
            )
        }
    }
    
    /**
     * 检测是否为照片（通过EXIF信息）
     */
    private fun detectIfPhotograph(imageFile: File): Boolean {
        return try {
            val exif = ExifInterface(imageFile.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
            
            // 如果有相机信息或特定EXIF标签，很可能是照片
            make != null || model != null || orientation != -1
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测是否为截图
     */
    private fun detectIfScreenshot(
        fileName: String,
        width: Int,
        height: Int
    ): Boolean {
        // 截图命名特征
        val screenshotKeywords = listOf(
            "screenshot", "截屏", "screen", "capture", "scr"
        )
        
        val lowerName = fileName.lowercase(Locale.getDefault())
        val hasScreenshotName = screenshotKeywords.any { lowerName.contains(it) }
        
        // 截图尺寸特征（常见设备分辨率）
        val commonResolutions = listOf(
            Pair(1080, 1920), // 竖屏
            Pair(1920, 1080), // 横屏
            Pair(1440, 2560),
            Pair(2560, 1440),
            Pair(2160, 3840),
            Pair(3840, 2160)
        )
        
        val isCommonResolution = commonResolutions.any { (w, h) ->
            (width == w && height == h) || (width == h && height == w)
        }
        
        return hasScreenshotName || isCommonResolution
    }
}
```

## **五、EXIF信息处理**

```kotlin
object ExifDataHandler {
    
    /**
     * 复制EXIF信息
     */
    fun copyExifData(sourceFile: File, targetFile: File): Boolean {
        return try {
            val sourceExif = ExifInterface(sourceFile.absolutePath)
            val targetExif = ExifInterface(targetFile.absolutePath)
            
            // 复制所有重要的EXIF标签
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_DATETIME)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_DATETIME_DIGITIZED)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_DATETIME_ORIGINAL)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_GPS_LATITUDE)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_GPS_LATITUDE_REF)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_GPS_LONGITUDE)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_GPS_LONGITUDE_REF)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_GPS_ALTITUDE)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_GPS_ALTITUDE_REF)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_MAKE)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_MODEL)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_ORIENTATION)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_X_RESOLUTION)
            copyExifAttribute(sourceExif, targetExif, ExifInterface.TAG_Y_RESOLUTION)
            
            targetExif.saveAttributes()
            true
        } catch (e: Exception) {
            Log.e("ExifDataHandler", "复制EXIF信息失败", e)
            false
        }
    }
    
    private fun copyExifAttribute(
        source: ExifInterface,
        target: ExifInterface,
        tag: String
    ) {
        val value = source.getAttribute(tag)
        if (value != null) {
            target.setAttribute(tag, value)
        }
    }
    
    /**
     * 清理EXIF隐私信息（可选）
     */
    fun stripExifPrivacyData(imageFile: File): Boolean {
        return try {
            val exif = ExifInterface(imageFile.absolutePath)
            
            // 移除GPS位置信息
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
            
            // 移除设备信息（可选）
            exif.setAttribute(ExifInterface.TAG_MAKE, null)
            exif.setAttribute(ExifInterface.TAG_MODEL, null)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
            
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

## **六、使用示例**

```kotlin
class ImageCompressionActivity : AppCompatActivity() {
    
    private lateinit var compressionManager: BatchCompressionManager
    private var currentTask: BatchCompressionManager.CancellableTask? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compression)
        
        compressionManager = BatchCompressionManager()
        
        setupViews()
    }
    
    private fun setupViews() {
        // 选择目录按钮
        findViewById<Button>(R.id.btn_select_dir).setOnClickListener {
            selectDirectory()
        }
        
        // 开始压缩按钮
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            startCompression()
        }
        
        // 停止按钮
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopCompression()
        }
    }
    
    private fun selectDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                   Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        
        startActivityForResult(intent, REQUEST_CODE_SELECT_DIR)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SELECT_DIR && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val directoryPath = DocumentFile.fromTreeUri(this, uri)?.uri?.path
                findViewById<TextView>(R.id.tv_selected_dir).text = "已选择: $directoryPath"
            }
        }
    }
    
    private fun startCompression() {
        val sourceDir = File(Environment.getExternalStorageDirectory(), "Pictures")
        val targetDir = File(getExternalFilesDir(null), "CompressedImages")
        
        val config = ImageCompressionManager.CompressionConfig(
            maxWidth = 1920,
            maxHeight = 1080,
            quality = 80,
            maxFileSizeKB = 1024, // 1MB以上才压缩
            keepExif = true
        )
        
        currentTask = compressionManager.compressDirectory(
            context = this,
            sourceDir = sourceDir,
            targetDir = targetDir,
            config = config,
            callback = object : BatchCompressionManager.CompressionCallback {
                override fun onStart(total: Int) {
                    runOnUiThread {
                        findViewById<ProgressBar>(R.id.progress_bar).max = total
                        findViewById<TextView>(R.id.tv_status).text = "开始压缩 $total 张图片"
                    }
                }
                
                override fun onProgress(
                    current: Int,
                    total: Int,
                    result: ImageCompressionManager.CompressionResult
                ) {
                    runOnUiThread {
                        findViewById<ProgressBar>(R.id.progress_bar).progress = current
                        updateProgressInfo(result)
                    }
                }
                
                override fun onComplete(results: List<ImageCompressionManager.CompressionResult>) {
                    runOnUiThread {
                        showCompressionSummary(results)
                    }
                }
                
                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@ImageCompressionActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    
    private fun updateProgressInfo(result: ImageCompressionManager.CompressionResult) {
        val info = """
            正在处理: ${result.originalFile.name}
            原始大小: ${formatFileSize(result.originalSize)}
            压缩后: ${formatFileSize(result.compressedSize)}
            节省: ${"%.1f".format(result.savedPercentage)}%
        """.trimIndent()
        
        findViewById<TextView>(R.id.tv_current_info).text = info
    }
    
    private fun showCompressionSummary(results: List<ImageCompressionManager.CompressionResult>) {
        val successful = results.count { it.success }
        val failed = results.size - successful
        
        val totalOriginalSize = results.sumOf { it.originalSize }
        val totalCompressedSize = results.sumOf { it.compressedSize }
        val totalSaved = totalOriginalSize - totalCompressedSize
        
        val summary = """
            压缩完成！
            
            总共处理: ${results.size} 张图片
            成功: $successful 张
            失败: $failed 张
            
            原始总大小: ${formatFileSize(totalOriginalSize)}
            压缩后总大小: ${formatFileSize(totalCompressedSize)}
            总共节省: ${formatFileSize(totalSaved)}
            
            平均压缩率: ${"%.1f".format(results.filter { it.success }.map { it.savedPercentage }.average())}%
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("压缩结果")
            .setMessage(summary)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun stopCompression() {
        currentTask?.cancel()
        Toast.makeText(this, "压缩已停止", Toast.LENGTH_SHORT).show()
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    companion object {
        private const val REQUEST_CODE_SELECT_DIR = 1001
    }
}
```

## **七、Gradle依赖配置**

```gradle
// build.gradle (app)
android {
    defaultConfig {
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
        }
    }
    
    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
        }
    }
}

dependencies {
    // 图片处理相关库
    implementation 'androidx.exifinterface:exifinterface:1.3.6'
    
    // 可选：使用第三方库获得更好性能
    implementation 'com.github.zomato:androidphotofilters:1.0.2'
    implementation 'id.zelory:compressor:3.0.1'
    
    // WebP支持
    implementation 'com.facebook.fresco:webpsupport:2.6.0'
    
    // HEIF/HEIC支持
    implementation 'com.davemorrissey.labs:subsampling-scale-image-view:3.10.0'
}
```

## **八、性能优化建议**

1. **内存优化**：
   - 使用`inSampleSize`减少解码内存
   - 及时调用`bitmap.recycle()`
   - 使用`Bitmap.Config.RGB_565`代替ARGB_8888（如果不需要透明度）

2. **速度优化**：
   - 多线程并行处理
   - 使用Native代码（Libjpeg-turbo）
   - 缓存解码结果

3. **质量优化**：
   - 渐进式JPEG编码
   - 智能质量选择
   - 保留重要EXIF信息

4. **兼容性**：
   - 降级策略支持旧Android版本
   - 格式自动检测和转换
   - 异常处理和恢复

## **九、总结**

这个实施方案提供了：

1. **多种压缩引擎**：适应不同场景和Android版本
2. **智能策略**：根据图片内容自动选择最佳压缩参数
3. **批量处理**：支持目录扫描和多线程压缩
4. **完整性保护**：保留EXIF信息，支持多种图片格式
5. **性能优化**：内存和速度优化，防止OOM

可以根据实际需求选择或组合使用这些方案，实现高效的图片压缩功能。