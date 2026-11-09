package kr.go.edu.boundary.signing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class SignatureImagesTest {
    @Test
    void rejectsOversizedHeaderBeforeReadingPixelData() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB), "png", buffer);
        // 서명과 IHDR만 남깁니다. 픽셀 데이터까지 읽으면 이 입력은 해상도 검사에 도달할 수 없습니다.
        byte[] header = java.util.Arrays.copyOf(buffer.toByteArray(), 33);
        ByteBuffer.wrap(header).putInt(16, 50_000).putInt(20, 50_000);
        var crc = new CRC32();
        crc.update(header, 12, 17);
        ByteBuffer.wrap(header).putInt(29, (int) crc.getValue());

        var error = assertThrows(IllegalArgumentException.class,
                () -> SignatureImages.parsePng(Base64.getEncoder().encodeToString(header)));
        assertTrue(error.getMessage().contains("해상도"));
    }

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

