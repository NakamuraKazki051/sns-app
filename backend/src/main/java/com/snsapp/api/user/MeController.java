package com.snsapp.api.user;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiException;
import com.snsapp.api.user.UserDtos.ProfileUpdateRequest;
import com.snsapp.api.user.UserDtos.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import java.util.Arrays;

/** 設定画面: 表示名とアイコンの変更 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final UserRepository userRepository;
    private final UserIconRepository userIconRepository;
    private final CurrentUser currentUser;

    public MeController(UserRepository userRepository, UserIconRepository userIconRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.userIconRepository = userIconRepository;
        this.currentUser = currentUser;
    }

    @PatchMapping("/profile")
    @Transactional
    public UserResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request, HttpServletRequest httpRequest) {
        User user = findMe(httpRequest);
        String displayName = request.displayName().strip();
        if (displayName.isEmpty()) {
            throw ApiException.badRequest("表示名を入力してください");
        }
        user.setDisplayName(displayName);
        return UserResponse.from(user);
    }

    @PutMapping("/icon")
    @Transactional
    public UserResponse putIcon(@RequestParam("icon") MultipartFile file, HttpServletRequest httpRequest)
            throws IOException {
        User user = findMe(httpRequest);
        if (file.getSize() > UserIcon.MAX_BYTES) {
            throw ApiException.payloadTooLarge("アイコン画像は1MBまでです");
        }
        byte[] data = file.getBytes();
        // ブラウザが送る Content-Type は書き換えられるので、ファイルの先頭のバイト列で形式を判定する
        String contentType = detectImageType(data);
        if (contentType == null) {
            throw ApiException.badRequest("アイコン画像は JPEG か PNG にしてください");
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

    private static String detectImageType(byte[] data) {
        if (startsWith(data, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(data, PNG_MAGIC)) {
            return "image/png";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
