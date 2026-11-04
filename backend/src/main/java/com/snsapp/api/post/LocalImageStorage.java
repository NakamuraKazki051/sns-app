package com.snsapp.api.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/** ローカル用: 画像をディスクのフォルダに置く(既定は backend/data/post-images。Git には入らない) */
public class LocalImageStorage implements ImageStorage {

    // key はサーバーが作る UUID だけ。念のため、フォルダの外を指す名前(../ など)は受け付けない
    private static final Pattern SAFE_KEY = Pattern.compile("[0-9a-f-]{36}");

    private final Path dir;

    public LocalImageStorage(Path dir) {
        this.dir = dir;
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        Files.createDirectories(dir);
        // 書きかけのファイルを読まれないよう、別名で書いてから名前を変える
        Path tmp = Files.createTempFile(dir, "upload-", ".tmp");
        try {
            Files.write(tmp, data);
            Files.move(tmp, path(key), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public byte[] get(String key) throws IOException {
        try {
            return Files.readAllBytes(path(key));
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }

    private Path path(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("画像の名前が不正です: " + key);
        }
        return dir.resolve(key);
    }
}
