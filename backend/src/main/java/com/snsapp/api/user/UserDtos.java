package com.snsapp.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class UserDtos {

    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255, message = "メールアドレスは255文字以内で入力してください") String email,
            // BCrypt only accepts up to 72 bytes; multi-byte passwords are additionally checked in PasswordEncoderConfig
            @NotBlank @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください") String password,
            // ユーザー名は URL(/@ユーザー名)に使うので、半角英数字と _ だけ
            @NotBlank(message = "ユーザー名を入力してください")
            @Pattern(regexp = User.USERNAME_PATTERN, message = "ユーザー名は半角英数字と _ で15文字以内で入力してください")
            String username,
            @NotBlank(message = "表示名を入力してください")
            @Size(max = User.DISPLAY_NAME_MAX, message = "表示名は50文字以内で入力してください")
            String displayName
    ) {
    }

    public record UpdateRequest(
            @Email @Size(max = 255, message = "メールアドレスは255文字以内で入力してください") String email,
            @NotBlank String currentPassword,
            @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください") String newPassword
    ) {
    }

    /** 送った項目だけ変える。自己紹介の160字は見た目の1文字で数えるので MeController で検証する */
    public record ProfileUpdateRequest(
            @Size(max = User.DISPLAY_NAME_MAX, message = "表示名は50文字以内で入力してください")
            String displayName,
            String bio
    ) {
    }

    /** ログインしている本人に返す。メールアドレスを含む */
    public record UserResponse(String id, String email, String username, String displayName, Instant iconUpdatedAt,
                               String bio, Instant createdAt) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getUsername(), user.getDisplayName(),
                    user.getIconUpdatedAt(), user.getBio(), user.getCreatedAt());
        }
    }

    /** 他のユーザーにも見せる部分(投稿の投稿者など) */
    public record PublicUser(String id, String username, String displayName, Instant iconUpdatedAt) {
        public static PublicUser from(User user) {
            return new PublicUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getIconUpdatedAt());
        }
    }

    /** ユーザー検索・フォロー一覧の1人分 */
    public record UserSummary(String id, String username, String displayName, Instant iconUpdatedAt, String bio) {
        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getIconUpdatedAt(),
                    user.getBio());
        }
    }

    /** ユーザーの一覧1ページ分。nextCursor を次のリクエストに渡すと続きを読める(続きが無ければ null) */
    public record UserPage(List<UserSummary> users, String nextCursor) {
    }

    /** ユーザーページ */
    public record ProfileResponse(String id, String username, String displayName, Instant iconUpdatedAt, String bio,
                                  long postsCount, long followingCount, long followersCount, boolean followedByMe,
                                  Instant createdAt) {
    }

    /** フォロー・解除の結果 */
    public record FollowResponse(boolean following, long followersCount) {
    }
}
