package com.snsapp.api.post;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

/**
 * 画像の置き場所を選ぶ。app.images.s3-bucket(環境変数 IMAGES_S3_BUCKET)があれば S3、無ければディスク。
 * AWS の EC2 では user_data がこの環境変数を入れる
 */
@Configuration
public class ImageStorageConfig {

    @Bean
    public ImageStorage imageStorage(@Value("${app.images.s3-bucket:}") String bucket,
                                     @Value("${app.images.s3-region:ap-northeast-1}") String region,
                                     @Value("${app.images.dir:data/post-images}") String dir) {
        if (!bucket.isBlank()) {
            return new S3ImageStorage(S3Client.builder().region(Region.of(region)).build(), bucket);
        }
        return new LocalImageStorage(Path.of(dir));
    }
}
