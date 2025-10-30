# NSFW Content Filter - Model Setup Guide

## Overview
This implementation uses TensorFlow Lite for on-device NSFW content detection in Telegram X. All processing happens locally on the device - no data is sent to external servers.

## Required Model

### Model Specifications
- **Model Name**: NSFW MobileNetV2 Quantized
- **File Name**: `nsfw_mobilenet.tflite`
- **Location**: `app/src/main/assets/models/nsfw_mobilenet.tflite`
- **Input Size**: 224x224 RGB image
- **Output**: 2 classes [safe, nsfw] with confidence scores (float array)
- **Model Size**: ~5MB (quantized version)

### Recommended Source
The recommended model can be obtained from:
- **NudeNet**: https://github.com/notAI-tech/NudeNet
- **NSFW Model (GantMan)**: https://github.com/GantMan/nsfw_model

### Converting to TensorFlow Lite

If you have a TensorFlow model, convert it to TFLite format:

```python
import tensorflow as tf

# Load your trained model
model = tf.keras.models.load_model('nsfw_model.h5')

# Convert to TensorFlow Lite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# Save the model
with open('nsfw_mobilenet.tflite', 'wb') as f:
    f.write(tflite_model)
```

### Alternative: Pre-trained Models

You can download pre-converted TFLite models from:
1. TensorFlow Hub: https://tfhub.dev/
2. Model Zoo repositories
3. Community-contributed models on GitHub

**Important**: Ensure the model you use:
- Accepts 224x224 RGB input
- Outputs 2 classes (safe/nsfw)
- Is properly quantized for mobile use
- Has acceptable accuracy (>90% recommended)

## Installation Steps

1. **Obtain the Model File**
   - Download or train an NSFW detection model
   - Convert to TFLite format if needed
   - Ensure file is named `nsfw_mobilenet.tflite`

2. **Place in Assets**
   ```bash
   cp nsfw_mobilenet.tflite app/src/main/assets/models/
   ```

3. **Verify Integration**
   - Build the app
   - Check logs for "NSFW detector initialized successfully"
   - If model fails to load, check file path and format

## Model Performance

### Expected Behavior
- **Detection Time**: ~50-200ms per image on modern devices
- **Memory Usage**: ~10-20MB
- **Accuracy**: Depends on model quality (typically 85-95%)

### Adjusting Sensitivity

The detection threshold can be adjusted in code:

```java
NSFWDetector detector = NSFWDetector.getInstance(context);
detector.setThreshold(0.5f); // Default: 0.5
```

- **Higher threshold (0.7-0.9)**: More strict, fewer false positives
- **Lower threshold (0.3-0.5)**: More lenient, fewer false negatives

## Testing

To test the implementation:

1. Load images with known NSFW/safe content
2. Check application logs for detection results
3. Verify placeholder is shown for NSFW content
4. Confirm save functionality is disabled for blocked content

## Troubleshooting

### Model Not Loading
- Check file exists at `app/src/main/assets/models/nsfw_mobilenet.tflite`
- Verify file is not corrupted
- Ensure TensorFlow Lite dependencies are included in build.gradle

### Poor Detection Accuracy
- Try a different pre-trained model
- Adjust detection threshold
- Ensure model input preprocessing matches training

### Performance Issues
- Use quantized model (int8)
- Reduce input size if possible
- Consider caching results

## Privacy & Security

- All detection happens on-device
- No data is transmitted to external servers
- Original images are not modified
- Detection results are cached locally only

## Legal Considerations

Ensure your use of NSFW detection models complies with:
- Model license terms
- App store guidelines
- Local regulations regarding content filtering
- User privacy requirements
