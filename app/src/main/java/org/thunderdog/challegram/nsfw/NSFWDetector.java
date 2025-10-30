/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.nsfw;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.thunderdog.challegram.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-device NSFW content detector using TensorFlow Lite.
 * 
 * This detector analyzes images locally without sending data to any external server.
 * It uses a lightweight MobileNet-based model optimized for mobile devices.
 * 
 * Model Information:
 * - Expected model: NSFW MobileNetV2 Quantized (*.tflite)
 * - Model location: assets/models/nsfw_mobilenet.tflite
 * - Input: 224x224 RGB image
 * - Output: 2 classes [safe, nsfw] with confidence scores
 * - Recommended model: https://github.com/GantMan/nsfw_model (converted to TFLite)
 * 
 * Usage:
 * - Detection threshold: 0.5 (adjustable via setThreshold)
 * - Results are cached to avoid re-analyzing the same content
 * - Thread-safe for concurrent analysis requests
 */
public class NSFWDetector {
  private static final String TAG = "NSFWDetector";
  private static final String MODEL_PATH = "models/nsfw_mobilenet.tflite";
  
  // Model input/output dimensions
  private static final int INPUT_SIZE = 224;
  private static final int NUM_CHANNELS = 3;
  private static final int NUM_CLASSES = 2; // [safe, nsfw]
  
  // Detection threshold (0.0 to 1.0)
  private static final float DEFAULT_THRESHOLD = 0.5f;
  private float threshold = DEFAULT_THRESHOLD;
  
  // Singleton instance
  private static volatile NSFWDetector instance;
  
  // TensorFlow Lite interpreter
  private Interpreter interpreter;
  
  // Image preprocessing
  private ImageProcessor imageProcessor;
  
  // Result cache (bitmap hashcode -> NSFW score)
  private final LruCache<Integer, Float> resultCache;
  
  // Thread pool for async detection
  private final ExecutorService executorService;
  
  // Detection callback
  public interface DetectionCallback {
    void onDetectionComplete(boolean isNSFW, float confidence);
  }
  
  private NSFWDetector(@NonNull Context context) {
    // Initialize cache (max 200 entries)
    resultCache = new LruCache<>(200);
    
    // Thread pool with 2 threads for parallel processing
    executorService = Executors.newFixedThreadPool(2);
    
    // Initialize image processor
    imageProcessor = new ImageProcessor.Builder()
        .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build();
    
    // Load the TFLite model
    try {
      ByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_PATH);
      Interpreter.Options options = new Interpreter.Options();
      options.setNumThreads(2);
      interpreter = new Interpreter(modelBuffer, options);
      Log.i(TAG, "NSFW detector initialized successfully");
    } catch (IOException e) {
      Log.e(TAG, "Failed to load NSFW detection model. Filter will be disabled.", e);
      interpreter = null;
    } catch (Exception e) {
      Log.e(TAG, "Error initializing NSFW detector", e);
      interpreter = null;
    }
  }
  
  /**
   * Get singleton instance of NSFWDetector
   */
  public static NSFWDetector getInstance(@NonNull Context context) {
    if (instance == null) {
      synchronized (NSFWDetector.class) {
        if (instance == null) {
          instance = new NSFWDetector(context.getApplicationContext());
        }
      }
    }
    return instance;
  }
  
  /**
   * Check if the detector is ready to use
   */
  public boolean isReady() {
    return interpreter != null;
  }
  
  /**
   * Set the NSFW detection threshold (0.0 to 1.0)
   * Higher values = more strict (fewer false positives, more false negatives)
   * Lower values = more lenient (more false positives, fewer false negatives)
   */
  public void setThreshold(float threshold) {
    this.threshold = Math.max(0.0f, Math.min(1.0f, threshold));
    Log.i(TAG, "NSFW threshold set to: " + this.threshold);
  }
  
  /**
   * Synchronously detect if a bitmap contains NSFW content
   * 
   * @param bitmap The image to analyze
   * @return NSFWResult containing detection status and confidence
   */
  @Nullable
  public NSFWResult detectSync(@Nullable Bitmap bitmap) {
    if (!isReady() || bitmap == null || bitmap.isRecycled()) {
      return null;
    }
    
    // Check cache first
    int bitmapHash = getBitmapHash(bitmap);
    Float cachedScore = resultCache.get(bitmapHash);
    if (cachedScore != null) {
      return new NSFWResult(cachedScore >= threshold, cachedScore);
    }
    
    try {
      // Preprocess image
      TensorImage tensorImage = new TensorImage();
      tensorImage.load(bitmap);
      tensorImage = imageProcessor.process(tensorImage);
      
      // Prepare output buffer
      float[][] output = new float[1][NUM_CLASSES];
      
      // Run inference
      synchronized (this) {
        interpreter.run(tensorImage.getBuffer(), output);
      }
      
      // Extract NSFW confidence score
      float nsfwScore = output[0][1]; // Index 1 is NSFW class
      
      // Cache result
      resultCache.put(bitmapHash, nsfwScore);
      
      Log.d(TAG, "NSFW detection: score=" + nsfwScore + ", isNSFW=" + (nsfwScore >= threshold));
      
      return new NSFWResult(nsfwScore >= threshold, nsfwScore);
    } catch (Exception e) {
      Log.e(TAG, "Error during NSFW detection", e);
      return null;
    }
  }
  
  /**
   * Asynchronously detect if a bitmap contains NSFW content
   * 
   * @param bitmap The image to analyze
   * @param callback Callback to receive detection results
   */
  public void detectAsync(@Nullable final Bitmap bitmap, @NonNull final DetectionCallback callback) {
    if (!isReady() || bitmap == null || bitmap.isRecycled()) {
      callback.onDetectionComplete(false, 0.0f);
      return;
    }
    
    executorService.execute(new Runnable() {
      @Override
      public void run() {
        NSFWResult result = detectSync(bitmap);
        if (result != null) {
          callback.onDetectionComplete(result.isNSFW, result.confidence);
        } else {
          callback.onDetectionComplete(false, 0.0f);
        }
      }
    });
  }
  
  /**
   * Clear the result cache
   */
  public void clearCache() {
    resultCache.evictAll();
    Log.i(TAG, "NSFW detection cache cleared");
  }
  
  /**
   * Release resources
   */
  public void release() {
    if (interpreter != null) {
      interpreter.close();
      interpreter = null;
    }
    executorService.shutdown();
    clearCache();
    Log.i(TAG, "NSFW detector resources released");
  }
  
  /**
   * Generate a hash code for bitmap to use as cache key
   */
  private int getBitmapHash(Bitmap bitmap) {
    int result = bitmap.getWidth();
    result = 31 * result + bitmap.getHeight();
    result = 31 * result + bitmap.getConfig().hashCode();
    // Sample a few pixels for additional uniqueness
    if (bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
      int centerX = bitmap.getWidth() / 2;
      int centerY = bitmap.getHeight() / 2;
      result = 31 * result + bitmap.getPixel(centerX, centerY);
      if (bitmap.getWidth() > 10 && bitmap.getHeight() > 10) {
        result = 31 * result + bitmap.getPixel(10, 10);
        result = 31 * result + bitmap.getPixel(bitmap.getWidth() - 10, bitmap.getHeight() - 10);
      }
    }
    return result;
  }
  
  /**
   * Result of NSFW detection
   */
  public static class NSFWResult {
    public final boolean isNSFW;
    public final float confidence;
    
    public NSFWResult(boolean isNSFW, float confidence) {
      this.isNSFW = isNSFW;
      this.confidence = confidence;
    }
  }
}
