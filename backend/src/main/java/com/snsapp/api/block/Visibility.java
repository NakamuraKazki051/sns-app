package com.snsapp.api.block;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * ブロック・ミュート(F-20)で、見る人から見えなくする人を決める。
 * 一覧は DB の問い合わせの段階で除く(読んだあとで減らすと、1ページが20件に満たなくなる)
 */
@Component
public class Visibility {

    // NOT IN に空の一覧は渡せないので、誰の ID でもない値を1つ入れておく
    private static final String NOBODY = "-";

    private final BlockRepository blocks;
    private final MuteRepository mutes;

    public Visibility(BlockRepository blocks, MuteRepository mutes) {
        this.blocks = blocks;
        this.mutes = mutes;
    }

    /** ブロックしている人・されている人。投稿・リプライ・プロフィール・検索・フォロー一覧から除く */
    public Set<String> blockRelated(String viewerId) {
        Set<String> ids = new HashSet<>(blocks.findRelatedIds(viewerId));
        ids.add(NOBODY);
        return ids;
    }

    /** blockRelated にミュート中の人を足したもの。タイムライン・新着・リプライ一覧から除く */
    public Set<String> hidden(String viewerId) {
        Set<String> ids = blockRelated(viewerId);
        ids.addAll(mutes.findMutedIds(viewerId));
        return ids;
    }

    /** どちらかがどちらかをブロックしている */
    public boolean blockedBetween(String a, String b) {
        return blocks.existsBetween(a, b);
    }
}
