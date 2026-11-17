package com.snsapp.api.post;

import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.ImageFiles;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 送られてきた画像(投稿・DM)を確かめたもの。JPEG か PNG(ファイルの先頭のバイトで判定)、5MB まで、
 * 縦・横それぞれ 8192px まで。合わなければ 400(大きすぎれば 413)
 */
public record UploadedImage(byte[] data, String contentType, int width, int height) {

    public static UploadedImage read(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("画像を選んでください");
        }
        if (file.getSize() > PostImage.MAX_BYTES) {
            throw ApiException.payloadTooLarge("画像は5MBまでです");
        }
        byte[] data = file.getBytes();
        String contentType = ImageFiles.detectType(data);
        if (contentType == null) {
            throw ApiException.badRequest("画像は JPEG か PNG にしてください");
        }
        ImageFiles.Size size = ImageFiles.readSize(data);
        if (size == null) {
            throw ApiException.badRequest("画像を読み込めませんでした。別の画像でお試しください");
        }
        if (size.width() > PostImage.MAX_SIDE || size.height() > PostImage.MAX_SIDE) {
            throw ApiException.badRequest("画像が大きすぎます(縦・横それぞれ" + PostImage.MAX_SIDE + "ピクセルまで)");
        }
        return new UploadedImage(data, contentType, size.width(), size.height());
    }

    /** 置き場所での名前を付けて、DB に入れる形にする */
    public PostImage toPostImage(String key) {
        return new PostImage(key, contentType, width, height);
    }
}
