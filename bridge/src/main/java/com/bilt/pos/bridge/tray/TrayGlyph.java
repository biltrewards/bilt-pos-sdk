package com.bilt.pos.bridge.tray;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;

/**
 * Draws the menu-bar glyph: a bridge deck with an arch, in solid black on a transparent background.
 * Being a shape plus alpha only, it is a valid macOS template image, so with {@code
 * apple.awt.enableTemplateImages} the system tints it for light and dark menu bars. The image is
 * multi-resolution so Retina menu bars get the 2x rendering instead of an upscale.
 */
final class TrayGlyph {

  private static final int BASE_SIZE = 22;

  private TrayGlyph() {}

  static Image image() {
    return new BaseMultiResolutionImage(render(BASE_SIZE), render(BASE_SIZE * 2));
  }

  static BufferedImage render(int size) {
    BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setColor(Color.BLACK);
      float unit = size / 22f;
      g.setStroke(new BasicStroke(2f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // deck
      g.draw(new Line2D.Float(2 * unit, 15 * unit, 20 * unit, 15 * unit));
      // arch under the deck
      g.draw(new Arc2D.Float(4 * unit, 11 * unit, 14 * unit, 10 * unit, 0, 180, Arc2D.OPEN));
      // towers
      g.draw(new Line2D.Float(6 * unit, 15 * unit, 6 * unit, 5 * unit));
      g.draw(new Line2D.Float(16 * unit, 15 * unit, 16 * unit, 5 * unit));
      // main cable
      g.draw(new Arc2D.Float(6 * unit, 5 * unit, 10 * unit, 12 * unit, 0, 180, Arc2D.OPEN));
    } finally {
      g.dispose();
    }
    return img;
  }
}
