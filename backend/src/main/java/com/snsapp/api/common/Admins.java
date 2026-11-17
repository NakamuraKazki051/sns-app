package com.snsapp.api.common;

import com.snsapp.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理者(通報を見て対応する人)。設定 app.admin.usernames(環境変数 ADMIN_USERNAMES、カンマ区切りのユーザー名)で決める。
 * 画面から管理者を増やす仕組みは作らない(乗っ取られたアカウントが管理者を増やせないように)
 */
@Component
public class Admins {

    private final Set<String> usernames;
    private final UserRepository users;

    public Admins(@Value("${app.admin.usernames:}") String usernames, UserRepository users) {
        this.usernames = Arrays.stream(usernames.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.users = users;
    }

    public boolean isAdmin(String userId) {
        return !usernames.isEmpty() && users.findById(userId)
                .map(user -> usernames.contains(user.getUsername().toLowerCase(Locale.ROOT)))
                .orElse(false);
    }

    /** 管理者でなければ 404(管理者の API があることも知らせない) */
    public void require(String userId) {
        if (!isAdmin(userId)) {
            throw ApiException.notFound("見つかりません");
        }
    }
}
