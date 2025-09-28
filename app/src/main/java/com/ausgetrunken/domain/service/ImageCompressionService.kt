package com.ausgetrunken.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ImageCompressionService(
    private val context: Context
) {
    companion object {
        private const val TAG = "ImageCompressionService"
        
        // Compression settings for vineyard photos
        private const val MAX_WIDTH = 1200 // Max width in pixels for vineyard photos
        private const val MAX_HEIGHT = 1200 // Max height in pixels
        private const val JPEG_QUALITY = 85 // JPEG compression quality (0-100)
        private const val MAX_FILE_SIZE_KB = 500 // Target maximum file size in KB
        
        // For very large images, we might need to compress more aggressively
        private const val AGGRESSIVE_JPEG_QUALITY = 70
        private const val MIN_JPEG_QUALITY = 60
    }

    /**
     * Compress image from URI and save to target file
     * @param sourceUri Source image URI
     * @param targetFile Target file to save compressed image
     * @return Result with success/failure and final file size
     */
    suspend fun compressImage(sourceUri: Uri, targetFile: File): Result<Long> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== STARTING IMAGE COMPRESSION ===")
            Log.d(TAG, "Source URI: $sourceUri")
            Log.d(TAG, "Target file: ${targetFile.absolutePath}")
            
            // Read original image
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream for URI"))
            
            // Decode image dimensions first (without loading full bitmap)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            val originalFileSize = getFileSizeFromUri(sourceUri)
            
            Log.d(TAG, "Original dimensions: ${originalWidth}x${originalHeight}")
            Log.d(TAG, "Original file size: ${originalFileSize / 1024}KB")
            
            // Calculate optimal sample size
            val sampleSize = calculateSampleSize(originalWidth, originalHeight, MAX_WIDTH, MAX_HEIGHT)
            Log.d(TAG, "Calculated sample size: $sampleSize")
            
            // Load and decode bitmap with sample size
            val inputStream2 = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot reopen input stream"))
            
            val decodedBitmap = BitmapFactory.decodeStream(inputStream2, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            })
            inputStream2.close()
            
            if (decodedBitmap == null) {
                return@withContext Result.failure(Exception("Failed to decode bitmap"))
            }
            
            Log.d(TAG, "Decoded bitmap dimensions: ${decodedBitmap.width}x${decodedBitmap.height}")
            
            // Handle image rotation based on EXIF data
            val rotatedBitmap = handleImageRotation(sourceUri, decodedBitmap)
            if (rotatedBitmap != decodedBitmap) {
                decodedBitmap.recycle() // Free memory of original bitmap
            }
            
            // Further resize if still too large
            val finalBitmap = if (rotatedBitmap.width > MAX_WIDTH || rotatedBitmap.height > MAX_HEIGHT) {
                val scaledBitmap = createScaledBitmap(rotatedBitmap, MAX_WIDTH, MAX_HEIGHT)
                if (scaledBitmap != rotatedBitmap) {
                    rotatedBitmap.recycle()
                }
                scaledBitmap
            } else {
                rotatedBitmap
            }
            
            Log.d(TAG, "Final bitmap dimensions: ${finalBitmap.width}x${finalBitmap.height}")
            
            // Compress and save with adaptive quality
            val finalFileSize = compressAndSaveBitmap(finalBitmap, targetFile, originalFileSize)
            
            // Clean up bitmap
            finalBitmap.recycle()
            
            Log.d(TAG, "Compression completed successfully")
            Log.d(TAG, "Final file size: ${finalFileSize / 1024}KB")
            Log.d(TAG, "Compression ratio: ${(originalFileSize.toFloat() / finalFileSize * 100).toInt()}% of original")
            Log.d(TAG, "=== IMAGE COMPRESSION COMPLETED ===")
            
            Result.success(finalFileSize)
            
        } catch (e: Exception) {
            Log.e(TAG, "Image compression failed", e)
            Result.failure(e)
        }
    }

    /**
     * Compress image file and overwrite it
     */
    suspend fun compressImageFile(imageFile: File): Result<Long> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Compressing existing file: ${imageFile.absolutePath}")
            
            if (!imageFile.exists()) {
                return@withContext Result.failure(Exception("Image file does not exist"))
            }
            
            val originalSize = imageFile.length()
            Log.d(TAG, "Original file size: ${originalSize / 1024}KB")
            
            // Load bitmap from file
            val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext Result.failure(Exception("Failed to decode image file"))
            
            Log.d(TAG, "Original dimensions: ${originalBitmap.width}x${originalBitmap.height}")
            
            // Resize if needed
            val resizedBitmap = if (originalBitmap.width > MAX_WIDTH || originalBitmap.height > MAX_HEIGHT) {
                val scaled = createScaledBitmap(originalBitmap, MAX_WIDTH, MAX_HEIGHT)
                originalBitmap.recycle()
                scaled
            } else {
                originalBitmap
            }
            
            Log.d(TAG, "Resized dimensions: ${resizedBitmap.width}x${resizedBitmap.height}")
            
            // Compress and save
            val finalSize = compressAndSaveBitmap(resizedBitmap, imageFile, originalSize)
            resizedBitmap.recycle()
            
            Log.d(TAG, "File compression completed. New size: ${finalSize / 1024}KB")
            Result.success(finalSize)
            
        } catch (e: Exception) {
            Log.e(TAG, "File compression failed", e)
            Result.failure(e)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        var sampleSize = 1
        
        if (width > maxWidth || height > maxHeight) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            
            // Calculate the largest sampleSize that keeps both dimensions larger than requested
            while ((halfWidth / sampleSize) >= maxWidth && (halfHeight / sampleSize) >= maxHeight) {
                sampleSize *= 2
            }
        }
        
        return sampleSize
    }

    private fun createScaledBitmap(original: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = original.width
        val height = original.height
        
        // Calculate scale to fit within bounds while maintaining aspect ratio
        val scale = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )
        
        if (scale >= 1.0f) {
            // No scaling needed
            return original
        }
        
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        
        Log.d(TAG, "Scaling from ${width}x${height} to ${scaledWidth}x${scaledHeight} (scale: $scale)")
        
        return Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true)
    }

    private fun handleImageRotation(sourceUri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            // Try to read EXIF data from content resolver
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                inputStream.close()
                
                val rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                
                if (rotationDegrees != 0f) {
                    Log.d(TAG, "Rotating image by $rotationDegrees degrees")
                    val matrix = Matrix().apply {
                        postRotate(rotationDegrees)
                    }
                    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Could not read/apply EXIF rotation", e)
            bitmap
        }
    }

    private fun compressAndSaveBitmap(bitmap: Bitmap, targetFile: File, originalSize: Long): Long {
        var quality = JPEG_QUALITY
        var attempts = 0
        val maxAttempts = 3
        
        while (attempts < maxAttempts) {
            try {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                val compressedBytes = outputStream.toByteArray()
                outputStream.close()
                
                val compressedSize = compressedBytes.size.toLong()
                Log.d(TAG, "Compression attempt ${attempts + 1}: Quality=$quality, Size=${compressedSize / 1024}KB")
                
                // Check if size is acceptable or if we can't compress further
                if (compressedSize <= MAX_FILE_SIZE_KB * 1024 || quality <= MIN_JPEG_QUALITY) {
                    // Save to file
                    FileOutputStream(targetFile).use { fileOut ->
                        fileOut.write(compressedBytes)
                    }
                    
                    Log.d(TAG, "Final compression: Quality=$quality, Size=${compressedSize / 1024}KB")
                    return compressedSize
                }
                
                // Try with lower quality
                quality = maxOf(MIN_JPEG_QUALITY, quality - 15)
                attempts++
                
            } catch (e: IOException) {
                Log.e(TAG, "Error during compression attempt ${attempts + 1}", e)
                break
            }
        }
        
        // Fallback: save with minimum quality
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, MIN_JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()
            
            FileOutputStream(targetFile).use { fileOut ->
                fileOut.write(bytes)
            }
            
            Log.w(TAG, "Using fallback compression with quality $MIN_JPEG_QUALITY")
            return bytes.size.toLong()
            
        } catch (e: IOException) {
            Log.e(TAG, "Fallback compression failed", e)
            throw e
        }
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine file size from URI", e)
            0L
        }
    }
}