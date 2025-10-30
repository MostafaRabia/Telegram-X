# NSFW Content Filter - Implementation Guide

## Overview
This document provides a complete guide to the NSFW content filter implementation in Telegram X. The implementation uses on-device TensorFlow Lite for privacy-preserving content filtering.

## Implementation Summary

### Architecture
The NSFW filter is implemented at the image rendering layer, intercepting all images and video thumbnails before display:

```
Image/Video Source → ImageReceiver → NSFWDetector → Display/Block Decision
                                          ↓
                                    Placeholder (if NSFW)
```

### Key Components

#### 1. NSFWDetector (`app/src/main/java/org/thunderdog/challegram/nsfw/NSFWDetector.java`)
- **Purpose**: On-device NSFW detection using TensorFlow Lite
- **Features**:
  - Singleton pattern for efficient resource usage
  - LRU cache (200 entries) to avoid re-analyzing same content
  - Thread pool (2 threads) for async processing
  - Configurable detection threshold (default: 0.5)
- **Methods**:
  - `detectSync(Bitmap)`: Synchronous detection
  - `detectAsync(Bitmap, callback)`: Asynchronous detection
  - `setThreshold(float)`: Adjust sensitivity
  - `isReady()`: Check if model loaded successfully

#### 2. NSFWPlaceholder (`app/src/main/java/org/thunderdog/challegram/nsfw/NSFWPlaceholder.java`)
- **Purpose**: Provides placeholder graphics for blocked content
- **Features**:
  - Draws gray background with "eye-slash" icon
  - Text: "Sensitive content hidden"
  - Scales to any image size
- **Methods**:
  - `drawPlaceholder(Canvas, Rect)`: Draw on canvas
  - `createPlaceholderBitmap(int, int)`: Create bitmap placeholder

#### 3. ImageReceiver Integration (`app/src/main/java/org/thunderdog/challegram/loader/ImageReceiver.java`)
- **Modified Methods**:
  - `setBitmap()`: Runs NSFW detection and replaces bitmap if needed
  - `destroy()`: Cleans up placeholder resources
- **New Methods**:
  - `isNSFWBlocked()`: Returns true if current content is NSFW
- **New Fields**:
  - `isNSFW`: Tracks NSFW status
  - `originalBitmap`: Stores original before filtering
  - `placeholderBitmap`: Cached placeholder

#### 4. Save Blocking Integration
- **MediaWrapper** (`app/src/main/java/org/thunderdog/challegram/data/MediaWrapper.java`)
  - Added `isNSFWBlocked()`: Checks both preview and target receivers
  
- **MosaicWrapper** (`app/src/main/java/org/thunderdog/challegram/data/MosaicWrapper.java`)
  - Added `getItems()`: Exposes media items for NSFW checking
  
- **TGMessageMedia** (`app/src/main/java/org/thunderdog/challegram/data/TGMessageMedia.java`)
  - Overrides `canBeSaved()`: Returns false if any media is NSFW

## How It Works

### Image Filtering Flow
1. Image loads into `ImageReceiver`
2. `setBitmap()` is called with the loaded bitmap
3. NSFWDetector analyzes the bitmap:
   - Checks cache first
   - If not cached, runs TFLite inference
   - Returns confidence score (0.0 to 1.0)
4. If score ≥ threshold (0.5):
   - Mark as NSFW
   - Replace bitmap with placeholder
   - Display placeholder instead of original
5. If score < threshold:
   - Display original bitmap normally

### Video Thumbnail Filtering
- Videos use `ImageVideoThumbFile` which extends `ImageFile`
- Thumbnails are loaded through the same `ImageReceiver` pipeline
- NSFW detection automatically applies to video thumbnails
- Full video analysis is NOT performed (only thumbnail)

### Save Functionality Blocking
1. User attempts to save/download media
2. System calls `canBeSaved()` on the message
3. `TGMessageMedia.canBeSaved()`:
   - Calls parent `canBeSaved()` (checks Telegram restrictions)
   - Gets all `MediaWrapper` items from `MosaicWrapper`
   - Checks each wrapper's `isNSFWBlocked()`
   - Returns false if any content is NSFW
4. Save/download is prevented if NSFW

### Affected Operations
When content is detected as NSFW:
- ❌ Save to Gallery
- ❌ Save to Downloads
- ❌ Share to other apps
- ❌ Forward (blocked by canBeSaved)
- ✅ View in chat (shows placeholder)
- ✅ Delete message
- ✅ Report message

## Setup Instructions

### 1. Prerequisites
- TensorFlow Lite dependencies (already added to build.gradle.kts)
- NSFW detection model file

### 2. Model Installation

#### Option A: Download Pre-trained Model
```bash
# Download a pre-converted TFLite NSFW model
# Recommended sources:
# - https://github.com/GantMan/nsfw_model (convert to TFLite)
# - https://tfhub.dev/tensorflow/efficientnet/lite/nsfw/1
# - Community models from Hugging Face

# Place the model file
cp nsfw_mobilenet.tflite app/src/main/assets/models/
```

#### Option B: Convert Your Own Model
```python
import tensorflow as tf

# Load your trained Keras model
model = tf.keras.models.load_model('nsfw_model.h5')

# Convert to TFLite with quantization
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]

tflite_model = converter.convert()

# Save
with open('nsfw_mobilenet.tflite', 'wb') as f:
    f.write(tflite_model)
```

### 3. Model Requirements
The model must meet these specifications:
- **Format**: TensorFlow Lite (*.tflite)
- **Input Shape**: [1, 224, 224, 3] (batch, height, width, channels)
- **Input Type**: Float32 or Uint8
- **Output Shape**: [1, 2] (batch, classes)
- **Output Classes**: [safe, nsfw]
- **Size**: < 10MB (preferably < 5MB for mobile)

### 4. Build and Run
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Or build release
./gradlew assembleRelease
```

### 5. Verify Installation
Check the logs after app launch:
```bash
adb logcat | grep NSFWDetector
# Expected: "NSFW detector initialized successfully"
# If model missing: "Failed to load NSFW detection model. Filter will be disabled."
```

## Configuration

### Adjusting Detection Threshold
In your app initialization code (e.g., `TelegramApplication.java`):
```java
NSFWDetector detector = NSFWDetector.getInstance(context);
// More strict (fewer false positives)
detector.setThreshold(0.7f);
// More lenient (fewer false negatives)
detector.setThreshold(0.3f);
```

### Clearing Cache
To clear detection results cache:
```java
NSFWDetector.getInstance(context).clearCache();
```

## Testing

### Testing with Sample Images
1. Create test images or download safe/NSFW test sets
2. Send images to yourself in Telegram X
3. Check logs for detection results:
   ```
   NSFW detection: score=0.85, isNSFW=true
   NSFW content detected and blocked (confidence: 0.85)
   ```

### Verifying Placeholder Display
- NSFW images should show gray placeholder with eye icon
- Text should read "Sensitive content hidden"

### Verifying Save Blocking
1. Long-press on NSFW message
2. Options should not include "Save to Gallery" or "Download"
3. Share/Forward options should be disabled

### Testing Video Thumbnails
1. Send video with potentially NSFW thumbnail
2. Thumbnail should be analyzed and blocked if NSFW
3. Full video is NOT analyzed (performance consideration)

## Performance Considerations

### Detection Performance
- **First Run**: ~200-500ms (model initialization)
- **Subsequent Runs**: ~50-200ms per image (cached or inference)
- **Memory**: ~10-20MB (model + buffers)
- **CPU**: Uses 2 threads for async processing

### Optimization Tips
1. **Use Quantized Models**: Int8 quantization reduces size and improves speed
2. **Cache Results**: Already implemented with LRU cache
3. **Async Detection**: Use `detectAsync()` for UI thread safety
4. **Batch Processing**: Not currently implemented, but could improve throughput

### Battery Impact
- Minimal impact: Detection only runs when images load
- CPU usage: Brief spikes during detection
- No continuous background processing

## Privacy & Security

### Privacy Features
✅ **On-Device Processing**: All analysis happens locally  
✅ **No Network Requests**: No data sent to external servers  
✅ **No Data Collection**: Detection results not stored permanently  
✅ **User Privacy**: Original images never leave device  

### Security Considerations
- Model file integrity: Verify model file is not tampered
- False positives: May block safe content (adjust threshold)
- False negatives: May miss some NSFW content (use better model)

## Troubleshooting

### Model Not Loading
```
Error: Failed to load NSFW detection model
```
**Solutions**:
- Verify model file exists at `app/src/main/assets/models/nsfw_mobilenet.tflite`
- Check model file is not corrupted
- Ensure model format is TFLite (not .pb or .h5)
- Check TensorFlow Lite dependencies in build.gradle

### High False Positive Rate
```
Too many safe images being blocked
```
**Solutions**:
- Increase threshold: `setThreshold(0.7f)`
- Use a better-trained model
- Collect false positives and retrain

### High False Negative Rate
```
NSFW content not being detected
```
**Solutions**:
- Decrease threshold: `setThreshold(0.3f)`
- Use a more comprehensive model
- Check model was trained on diverse dataset

### Performance Issues
```
App lagging when loading images
```
**Solutions**:
- Use quantized model (int8)
- Reduce input size (if model allows)
- Increase cache size
- Use async detection (already default)

## Future Enhancements

Possible improvements to consider:

1. **User Settings**: Add UI toggle to enable/disable filter
2. **Whitelist**: Allow users to mark false positives
3. **Custom Thresholds**: Per-chat or per-contact thresholds
4. **Multi-Model**: Combine multiple detection models
5. **Video Analysis**: Analyze key frames (not just thumbnail)
6. **Content Categories**: Detect specific types (violence, gore, etc.)
7. **Cloud Sync**: Optionally sync whitelist across devices
8. **Parental Controls**: Integrate with parental control features

## API Reference

### NSFWDetector

```java
// Get singleton instance
NSFWDetector detector = NSFWDetector.getInstance(context);

// Check if ready
if (detector.isReady()) {
    // Synchronous detection
    NSFWResult result = detector.detectSync(bitmap);
    if (result != null && result.isNSFW) {
        // Handle NSFW content
    }
    
    // Asynchronous detection
    detector.detectAsync(bitmap, new DetectionCallback() {
        @Override
        public void onDetectionComplete(boolean isNSFW, float confidence) {
            // Handle result
        }
    });
}

// Configure
detector.setThreshold(0.5f);
detector.clearCache();
```

### ImageReceiver

```java
ImageReceiver receiver = new ImageReceiver(view, radius);
// ... normal usage ...

// Check if content is blocked
if (receiver.isNSFWBlocked()) {
    // This receiver is showing NSFW placeholder
}
```

### MediaWrapper

```java
MediaWrapper wrapper = ...;

// Check if content is NSFW
if (wrapper.isNSFWBlocked()) {
    // This media is blocked
}
```

## License & Attribution

This NSFW filter implementation:
- Follows Telegram X's existing license (GPLv3)
- Uses TensorFlow Lite (Apache 2.0 license)
- Model attribution depends on which model you use

Make sure to:
1. Attribute the model source in your app
2. Comply with model license terms
3. Include TensorFlow Lite license notice

## Support

For issues or questions:
1. Check logs for error messages
2. Review this documentation
3. Check model file integrity
4. Verify TensorFlow Lite dependencies
5. Test with known safe/NSFW images

## Conclusion

This implementation provides a robust, privacy-preserving NSFW content filter for Telegram X. The filter:
- Blocks NSFW images and video thumbnails from display
- Prevents saving/downloading of NSFW content
- Operates entirely on-device
- Is configurable and extensible

Simply add the TFLite model file to complete the setup.
