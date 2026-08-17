package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class JvnHubGraphiteControlStyleTest {

  @Test
  void stylesMemoryComboAndPopupRendererWithGraphitePalette() throws Exception {
    AtomicReference<JComboBox<String>> result = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      JComboBox<String> combo = new JComboBox<>(new String[] {"JDK default", "G1"});
      JvnHub.styleHubComboBox(combo, new Dimension(230, 34));
      result.set(combo);
    });

    JComboBox<String> combo = Objects.requireNonNull(result.get());
    assertEquals("HubComboBoxUI", combo.getUI().getClass().getSimpleName());
    assertEquals(Color.decode("#101010"), combo.getBackground());
    assertEquals(Color.decode("#f0f0f0"), combo.getForeground());
    assertEquals(new Dimension(230, 34), combo.getPreferredSize());
    Component normal = combo.getRenderer().getListCellRendererComponent(
        new JList<>(), "JDK default", 0, false, false);
    Color normalBackground = normal.getBackground();
    Component selected = combo.getRenderer().getListCellRendererComponent(
        new JList<>(), "G1", 1, true, false);
    assertTrue(normal instanceof JLabel);
    assertEquals(Color.decode("#101010"), normalBackground);
    assertEquals(Color.decode("#303030"), selected.getBackground());
    double comboBrightPixels = brightPixelRatio(combo);
    assertTrue(comboBrightPixels < 0.12,
        "combo must not render a native white field; bright ratio=" + comboBrightPixels);
  }

  @Test
  void stylesSpinnerEditorAndBothArrowButtonsWithoutNativeWhiteSurfaces() throws Exception {
    AtomicReference<JSpinner> result = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      JSpinner spinner = new JSpinner(new SpinnerNumberModel(4096, 128, 131_072, 128));
      JvnHub.styleHubSpinner(spinner, new Dimension(110, 32));
      result.set(spinner);
    });

    JSpinner spinner = Objects.requireNonNull(result.get());
    assertEquals("HubSpinnerUI", spinner.getUI().getClass().getSimpleName());
    assertEquals(Color.decode("#101010"), spinner.getBackground());
    assertEquals(new Dimension(110, 32), spinner.getPreferredSize());
    JTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    assertEquals(Color.decode("#101010"), field.getBackground());
    assertEquals(Color.decode("#f0f0f0"), field.getForeground());
    assertEquals(Color.decode("#9a9a9a"), field.getDisabledTextColor());

    long arrowButtons = Arrays.stream(spinner.getComponents())
        .filter(JButton.class::isInstance)
        .peek(component -> assertFalse(((JButton) component).isContentAreaFilled()))
        .count();
    assertEquals(2L, arrowButtons);
    spinner.setEnabled(false);
    assertTrue(Arrays.stream(spinner.getComponents())
        .filter(JButton.class::isInstance)
        .noneMatch(Component::isEnabled));
    assertFalse(field.isEnabled());
    assertTrue(brightPixelRatio(spinner) < 0.12, "disabled spinner must remain graphite");
  }

  private static double brightPixelRatio(Component component) throws Exception {
    Dimension size = component.getPreferredSize();
    BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    SwingUtilities.invokeAndWait(() -> {
      component.setSize(size);
      if (component instanceof java.awt.Container container) container.doLayout();
      Graphics2D graphics = image.createGraphics();
      component.paint(graphics);
      graphics.dispose();
    });
    int bright = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        Color color = new Color(image.getRGB(x, y), true);
        if (color.getAlpha() > 0 && color.getRed() > 235
            && color.getGreen() > 235 && color.getBlue() > 235) {
          bright++;
        }
      }
    }
    return (double) bright / (image.getWidth() * image.getHeight());
  }
}
