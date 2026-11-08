package com.snsapp.api.post;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 画像付きの投稿の途中で失敗したとき(F-14) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostImageFailureTest extends ApiTestSupport {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ImageStorage imageStorage;

    @MockitoSpyBean
    private PostViews postViews;

    @Test
    void imageStaysWhenTheResponseFailsAfterThePostIsSaved() throws Exception {
        Cookie session = registerAndLogin();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "png", png);
        long before = postRepository.count();
        doThrow(new IllegalStateException("応答を作れない")).when(postViews).one(any(), any());

        try {
            mockMvc.perform(multipart("/api/posts")
                    .file(new MockMultipartFile("image", "photo", "image/png", png.toByteArray()))
                    .cookie(session));
        } catch (Exception expected) {
            // 応答には失敗する。確かめたいのは、そのあとに残るもの
        }

        // 投稿は保存済みなので、画像だけ消して表示が壊れた投稿にしてはいけない
        assertEquals(before + 1, postRepository.count());
        PostImage image = postRepository.findAll().stream()
                .map(Post::getImage)
                .filter(i -> i != null)
                .reduce((first, second) -> second)
                .orElseThrow();
        byte[] stored = imageStorage.get(image.key());
        imageStorage.delete(image.key());
        assertNotNull(stored);
    }
}
