package com.snsapp.api.post;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 投稿に付けた画像の情報(本体は ImageStorage に置く)。画像が無い投稿では、列が全部 null で、これ自体が null になる。
 * key は置き場所での名前(推測されないよう UUID)
 */
@Embeddable
public record PostImage(
        @Column(name = "image_key", length = 64) String key,
        @Column(name = "image_content_type", length = 20) String contentType,
        @Column(name = "image_width") Integer width,
        @Column(name = "image_height") Integer height) {

    /** 1枚の上限(要件 F-14)。画面は送る前に縮めるので、ふつうはずっと小さい */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** 縦・横それぞれの上限。小さいファイルで巨大な画像(展開するとメモリを食い尽くす)を送られるのを防ぐ */
    public static final int MAX_SIDE = 8192;
}
