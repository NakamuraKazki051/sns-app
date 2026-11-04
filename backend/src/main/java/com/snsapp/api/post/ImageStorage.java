package com.snsapp.api.post;

import java.io.IOException;

/**
 * 投稿の画像の置き場所。ローカルはディスク(LocalImageStorage)、AWS は S3(S3ImageStorage)。
 * どちらを使うかは ImageStorageConfig が設定で決める
 */
public interface ImageStorage {

    void put(String key, byte[] data, String contentType) throws IOException;

    /** 無ければ null */
    byte[] get(String key) throws IOException;

    /** 無くてもエラーにしない */
    void delete(String key) throws IOException;
}
