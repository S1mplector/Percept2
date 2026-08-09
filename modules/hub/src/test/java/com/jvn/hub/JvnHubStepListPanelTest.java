package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class JvnHubStepListPanelTest {
  @Test
  void stepListPaintClearsTheCanvasBeforeDrawingRows() throws Exception {
    Class<?> panelClass = Class.forName("com.jvn.hub.JvnHub$StepListPanel");
    Constructor<?> constructor = panelClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    JComponent panel = (JComponent) constructor.newInstance();
    panel.setFont(new JLabel().getFont());
    panel.setSize(220, 70);

    BufferedImage canvas = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D base = canvas.createGraphics();
    base.setColor(Color.MAGENTA);
    base.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    base.dispose();

    int background = panel.getBackground().getRGB();
    paint(panel, canvas);
    assertEquals(background, canvas.getRGB(110, 35));
  }

  private static void paint(JComponent panel, BufferedImage canvas) {
    Graphics2D graphics = canvas.createGraphics();
    try {
      panel.paint(graphics);
    } finally {
      graphics.dispose();
    }
  }
}
