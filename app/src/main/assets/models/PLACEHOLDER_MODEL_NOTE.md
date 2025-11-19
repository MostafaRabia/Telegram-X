# PLACEHOLDER MODEL - FOR TESTING ONLY

This `nsfw_mobilenet.tflite` file is a **placeholder model** created for integration testing purposes.

## Important Notes

⚠️ **This model is NOT trained for NSFW detection!**

- It will classify all images as "safe" (untrained weights)
- Created only to demonstrate the integration and allow building/testing the app
- Model size: ~70KB (much smaller than production models which are 5-20MB)

## For Production Use

Replace this file with a properly trained NSFW detection model:

1. Download from recommended sources (see `README.md`)
2. Ensure model matches specifications:
   - Input: 224x224x3 RGB
   - Output: 2 classes [safe, nsfw]
3. Replace this placeholder file

## Model Details

- Architecture: Simple CNN (Conv2D layers + Dense)
- Input shape: (1, 224, 224, 3)
- Output shape: (1, 2) [safe_probability, nsfw_probability]
- Format: TensorFlow Lite with dynamic range quantization
- Created: 2025-10-30

This placeholder allows you to:
- ✅ Build and install the app
- ✅ Test the UI integration
- ✅ Verify placeholder display works
- ✅ Test save blocking mechanism
- ❌ Actual NSFW content detection (not trained)
