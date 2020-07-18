package util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class Canvas extends JPanel {
  private BufferedImage bufferedImage;

  @Override
  public void paint(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;
    if (bufferedImage != null) {
      g2d.drawImage(bufferedImage, 0, 0, null);
    }
  }

  public void setImage(BufferedImage bufferedImage) {
    this.bufferedImage = bufferedImage;
    repaint();
  }
}
