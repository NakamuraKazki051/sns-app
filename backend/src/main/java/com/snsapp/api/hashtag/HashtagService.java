package com.snsapp.api.hashtag;

import com.snsapp.api.post.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 投稿のハッシュタグを本文に合わせる(F-22)。投稿・編集のあとに呼ぶ */
@Service
public class HashtagService {

    private final PostHashtagRepository hashtags;

    public HashtagService(PostHashtagRepository hashtags) {
        this.hashtags = hashtags;
    }

    @Transactional
    public void sync(Post post) {
        hashtags.deleteByPostId(post.getId());
        for (String tag : Hashtags.extract(post.getBody())) {
            hashtags.save(new PostHashtag(post.getId(), tag, post.getCreatedAt()));
        }
    }
}
