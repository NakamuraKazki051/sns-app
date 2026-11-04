package com.snsapp.api.common;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/** アップロードされた画像の形式と大きさを調べる(アイコンと投稿の画像で共通) */
public final class ImageFiles {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private ImageFiles() {
    }

    public record Size(int width, int height) {
    }

    /**
     * JPEG なら image/jpeg、PNG なら image/png、それ以外は null。
     * ブラウザが送る Content-Type は書き換えられるので、ファイルの先頭のバイト列で判定する
     */
    public static String detectType(byte[] data) {
        if (startsWith(data, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(data, PNG_MAGIC)) {
            return "image/png";
        }
        return null;
    }

    /**
     * 幅と高さ。画像全体は展開せず、先頭の情報だけを読む(大きな画像でもメモリを使わない)。
     * 読めなければ(壊れたファイルなど) null
     */
    public static Size readSize(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new Size(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
