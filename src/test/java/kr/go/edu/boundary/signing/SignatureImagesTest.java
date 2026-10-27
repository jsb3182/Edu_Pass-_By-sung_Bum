package kr.go.edu.boundary.signing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class SignatureImagesTest {
    @Test
    void acceptsDrawnSignatureAndRejectsBlankCanvas() throws Exception {
        BufferedImage image = new BufferedImage(300, 120, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 300, 120);
        ByteArrayOutputStream blank = new ByteArrayOutputStream();
        ImageIO.write(image, "png", blank);
        assertThrows(IllegalArgumentException.class,
                () -> SignatureImages.parsePng(Base64.getEncoder().encodeToString(blank.toByteArray())));

        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(4));
        graphics.drawLine(30, 50, 250, 70);
        graphics.dispose();
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        ImageIO.write(image, "png", written);
        byte[] png = written.toByteArray();
        assertArrayEquals(png, SignatureImages.parsePng(Base64.getEncoder().encodeToString(png)));
    }
}

