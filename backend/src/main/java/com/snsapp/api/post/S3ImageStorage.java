package com.snsapp.api.post;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * AWS 用: 画像を S3 のバケット(非公開)に置く。画面には S3 の URL を出さず、アプリが読んで返す
 * (ログインしている人だけが見られるようにするため)。
 * 認証は EC2 のロール(IAM)をそのまま使うので、アクセスキーは要らない
 */
public class S3ImageStorage implements ImageStorage {

    private static final String PREFIX = "post-images/";

    private final S3Client s3;
    private final String bucket;

    public S3ImageStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(PREFIX + key).contentType(contentType), RequestBody.fromBytes(data));
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(b -> b.bucket(bucket).key(PREFIX + key)).asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(PREFIX + key));
    }
}
