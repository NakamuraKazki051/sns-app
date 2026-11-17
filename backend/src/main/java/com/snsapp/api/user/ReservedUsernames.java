package com.snsapp.api.user;

import java.util.Locale;
import java.util.Set;

/**
 * 登録できないユーザー名。運営になりすませる名前と、画面・API の URL と紛らわしい名前。
 * 大文字・小文字は区別しない(Admin も ADMIN も使えない)
 */
public final class ReservedUsernames {

    private static final Set<String> NAMES = Set.of(
            // 運営・公式に見える名前
            "admin", "administrator", "root", "system", "sysadmin", "support", "help", "helpdesk",
            "official", "staff", "moderator", "mod", "owner", "security", "info", "contact", "noreply",
            "no_reply", "raiseline", "raiseline_jp", "team",
            // 画面の URL(/login、/settings など)や API と重なる名前
            "me", "api", "login", "logout", "register", "signup", "signin", "settings", "search",
            "messages", "notifications", "hashtag", "hashtags", "posts", "post", "users", "user",
            "home", "explore", "timeline", "trends", "verify_email", "reset_password", "forgot_password",
            "about", "terms", "privacy", "static", "assets",
            // プログラムで特別な意味を持つ値
            "null", "undefined", "anonymous", "everyone", "all");

    private ReservedUsernames() {
    }

    public static boolean contains(String username) {
        return NAMES.contains(username.toLowerCase(Locale.ROOT));
    }
}
