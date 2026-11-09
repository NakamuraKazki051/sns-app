package com.snsapp.api.hashtag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本文から「#タグ」を取り出す(F-22)。画面(frontend/src/hashtags.ts)も同じ決まりでリンクにする。
 * - # か ＃ のあとの、文字・数字・_ の並び(日本語も可)。50字まで
 * - 直前が文字・数字・_ なら取らない(「C#」「a#b」はタグではない)
 * - 数字だけのものは取らない(「#1」は番号のことが多い)
 * - 大文字・小文字は区別しない(小文字にそろえる)
 */
public final class Hashtags {

    public static final int TAG_MAX = 50;
    private static final Pattern TAG = Pattern.compile("(?<![\\p{L}\\p{N}_])[#＃]([\\p{L}\\p{N}_]{1," + TAG_MAX + "})(?![\\p{L}\\p{N}_])");
    private static final Pattern DIGITS = Pattern.compile("\\p{N}+");

    private Hashtags() {
    }

    /** 本文に出てくる順で、重複なし */
    public static Set<String> extract(String body) {
        Set<String> tags = new LinkedHashSet<>();
        if (body == null) {
            return tags;
        }
        Matcher matcher = TAG.matcher(body);
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (!DIGITS.matcher(tag).matches()) {
                tags.add(normalize(tag));
            }
        }
        return tags;
    }

    /** URL などで受け取ったタグを、保存した形にそろえる。先頭の # は外す */
    public static String normalize(String tag) {
        String value = tag.startsWith("#") || tag.startsWith("＃") ? tag.substring(1) : tag;
        return value.toLowerCase(Locale.ROOT);
    }
}
