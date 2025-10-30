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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Provides placeholder graphics for NSFW content that has been blocked.
 * Displays a blurred/gray background with a message indicating sensitive content.
 */
public class NSFWPlaceholder {
  
  private static final int PLACEHOLDER_COLOR = 0xFFE0E0E0; // Light gray
  private static final int TEXT_COLOR = 0xFF808080; // Medium gray
  private static final int ICON_COLOR = 0xFF606060; // Dark gray
  
  /**
   * Draw NSFW placeholder on canvas
   * 
   * @param canvas Canvas to draw on
   * @param bounds Bounds to draw within
   */
  public static void drawPlaceholder(@NonNull Canvas canvas, @NonNull Rect bounds) {
    drawPlaceholder(canvas, bounds.left, bounds.top, bounds.right, bounds.bottom);
  }
  
  /**
   * Draw NSFW placeholder on canvas
   * 
   * @param canvas Canvas to draw on
   * @param left Left coordinate
   * @param top Top coordinate
   * @param right Right coordinate
   * @param bottom Bottom coordinate
   */
  public static void drawPlaceholder(@NonNull Canvas canvas, int left, int top, int right, int bottom) {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    // Draw background
    paint.setColor(PLACEHOLDER_COLOR);
    paint.setStyle(Paint.Style.FILL);
    canvas.drawRect(left, top, right, bottom);
    
    // Calculate center
    int width = right - left;
    int height = bottom - top;
    int centerX = left + width / 2;
    int centerY = top + height / 2;
    
    // Draw icon (simple eye-slash representation)
    paint.setColor(ICON_COLOR);
    paint.setStyle(Paint.Style.STROKE);
    float iconSize = Math.min(width, height) * 0.15f;
    paint.setStrokeWidth(iconSize * 0.1f);
    
    // Draw circle (eye)
    canvas.drawCircle(centerX, centerY - iconSize * 0.3f, iconSize * 0.5f, paint);
    
    // Draw slash line
    float slashStart = iconSize * 0.8f;
    canvas.drawLine(
        centerX - slashStart, centerY - iconSize * 0.3f + slashStart,
        centerX + slashStart, centerY - iconSize * 0.3f - slashStart,
        paint
    );
    
    // Draw text
    paint.setColor(TEXT_COLOR);
    paint.setStyle(Paint.Style.FILL);
    paint.setTextAlign(Paint.Align.CENTER);
    float textSize = Math.min(width, height) * 0.08f;
    paint.setTextSize(textSize);
    
    String text = "Sensitive content hidden";
    canvas.drawText(text, centerX, centerY + iconSize * 1.2f, paint);
  }
  
  /**
   * Create a bitmap placeholder for NSFW content
   * 
   * @param width Width of the placeholder bitmap
   * @param height Height of the placeholder bitmap
   * @return Bitmap with NSFW placeholder graphics
   */
  @NonNull
  public static Bitmap createPlaceholderBitmap(int width, int height) {
    Bitmap bitmap = Bitmap.createBitmap(
        Math.max(1, width),
        Math.max(1, height),
        Bitmap.Config.ARGB_8888
    );
    Canvas canvas = new Canvas(bitmap);
    drawPlaceholder(canvas, 0, 0, width, height);
    return bitmap;
  }
}
