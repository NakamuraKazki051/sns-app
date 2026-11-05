package com.snsapp.api.user;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiErrors;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.common.ImageFiles;
import com.snsapp.api.common.OpenApiConfig;
import com.snsapp.api.common.TextLength;
import com.snsapp.api.user.UserDtos.ProfileUpdateRequest;
import com.snsapp.api.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 設定画面: 表示名とアイコンの変更 */
@RestController
@RequestMapping("/api/me")
@Tag(name = OpenApiConfig.TAG_ME)
public class MeController {

    // 見た目で160字でも、結合文字を大量に重ねた文字列は巨大になりうる。数える前に生の長さでも止める
    private static final int BIO_MAX_CHARS = 2000;


    private final UserRepository userRepository;
    private final UserIconRepository userIconRepository;
    private final CurrentUser currentUser;

    public MeController(UserRepository userRepository, UserIconRepository userIconRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.userIconRepository = userIconRepository;
        this.currentUser = currentUser;
    }

    @Operation(operationId = "updateProfile", summary = "表示名と自己紹介を変える",
            description = "送った項目だけ変える。表示名は50字まで。自己紹介は160字まで(絵文字も1字と数える)で、空にすると消える")
    @ApiErrors(400)
    @PatchMapping("/profile")
    @Transactional
    public UserResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request, HttpServletRequest httpRequest) {
        User user = findMe(httpRequest);
        if (request.displayName() != null) {
            String displayName = request.displayName().strip();
            if (displayName.isEmpty()) {
                throw ApiException.badRequest("表示名を入力してください");
            }
            user.setDisplayName(displayName);
        }
        if (request.bio() != null) {
            // 改行は残す。空にすると自己紹介を消す
            String bio = request.bio().strip();
            if (bio.length() > BIO_MAX_CHARS || TextLength.visible(bio) > User.BIO_MAX) {
                throw ApiException.badRequest("自己紹介は" + User.BIO_MAX + "文字以内で入力してください");
            }
            user.setBio(bio.isEmpty() ? null : bio);
        }
        return UserResponse.from(user);
    }

    @Operation(operationId = "putIcon", summary = "アイコンを変える",
            description = "JPEG か PNG、1MBまで")
    @ApiErrors({400, 413})
    @PutMapping(path = "/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public UserResponse putIcon(@RequestParam("icon") MultipartFile file, HttpServletRequest httpRequest)
            throws IOException {
        User user = findMe(httpRequest);
        if (file.getSize() > UserIcon.MAX_BYTES) {
            throw ApiException.payloadTooLarge("アイコン画像は1MBまでです");
        }
        byte[] data = file.getBytes();
        // ブラウザが送る Content-Type は書き換えられるので、ファイルの先頭のバイト列で形式を判定する
        String contentType = ImageFiles.detectType(data);
        if (contentType == null) {
            throw ApiException.badRequest("アイコン画像は JPEG か PNG にしてください");
        }
        // 先頭だけ画像に見せかけた壊れたファイルは保存しない
        if (ImageFiles.readSize(data) == null) {
            throw ApiException.badRequest("画像を読み込めませんでした。別の画像でお試しください");
        }
        // ミリ秒に揃えて、DB に保存したあとも応答と同じ値になるようにする
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UserIcon icon = userIconRepository.findById(user.getId()).orElseGet(() -> new UserIcon(user.getId()));
        icon.replace(contentType, data, now);
        userIconRepository.save(icon);
        user.setIconUpdatedAt(now);
        return UserResponse.from(user);
    }

    /** アイコンを外して、表示名の頭文字の丸に戻す */
    @Operation(operationId = "deleteIcon", summary = "アイコンを外す",
            description = "表示名の頭文字の丸に戻る")
    @DeleteMapping("/icon")
    @Transactional
    public UserResponse deleteIcon(HttpServletRequest httpRequest) {
        User user = findMe(httpRequest);
        userIconRepository.deleteById(user.getId());
        user.setIconUpdatedAt(null);
        return UserResponse.from(user);
    }

    private User findMe(HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        return userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("ログインしていません"));
    }
}
