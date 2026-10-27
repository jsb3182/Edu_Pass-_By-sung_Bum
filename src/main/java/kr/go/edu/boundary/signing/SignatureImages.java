package kr.go.edu.boundary.signing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

final class SignatureImages {
    private static final byte[] PNG_HEADER = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private SignatureImages() { }

    static byte[] parsePng(String encoded) {
        if (encoded == null || encoded.length() > 400_000) {
            throw new IllegalArgumentException("서명 이미지가 너무 큽니다.");
        }
        byte[] image;
        try {
            image = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("서명 이미지 형식이 올바르지 않습니다.");
        }
        if (image.length < PNG_HEADER.length || image.length > 256_000) {
            throw new IllegalArgumentException("서명 이미지 크기가 올바르지 않습니다.");
        }
        for (int index = 0; index < PNG_HEADER.length; index++) {
            if (image[index] != PNG_HEADER[index]) {
                throw new IllegalArgumentException("PNG 서명 이미지만 허용합니다.");
            }
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null || decoded.getWidth() < 100 || decoded.getHeight() < 50
                    || (long) decoded.getWidth() * decoded.getHeight() > 2_000_000) {
                throw new IllegalArgumentException("서명 이미지 해상도가 올바르지 않습니다.");
            }
            int inkPixels = 0;
            for (int y = 0; y < decoded.getHeight(); y += 2) {
                for (int x = 0; x < decoded.getWidth(); x += 2) {
                    int argb = decoded.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xff;
                    int red = (argb >>> 16) & 0xff;
                    int green = (argb >>> 8) & 0xff;
                    int blue = argb & 0xff;
                    if (alpha > 128 && red + green + blue < 600 && ++inkPixels >= 20) {
                        return image;
                    }
                }
            }
            throw new IllegalArgumentException("비어 있는 서명은 저장할 수 없습니다.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("서명 이미지를 읽을 수 없습니다.");
        }
    }
}
