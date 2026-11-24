package com.snsapp.api.auth;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 登録の有無を明かしてはいけないメール(パスワードの再設定・確認メールの再送)を、応答を返したあとに送る。
 * その場で送ると、登録済みのアドレスだけ Gmail とのやり取りの分(1秒ほど)応答が遅くなり、
 * 応答の速さから登録の有無を推測できてしまうため。
 * 送れなかったことも呼んだ人には伝えない(ログには残す)。
 * テストは app.mail.background を false にして、その場で送る(テストのトランザクションの中で確かめるため)
 */
@Component
public class BackgroundMail {

    private static final Logger log = LoggerFactory.getLogger(BackgroundMail.class);

    // 回数の制限(宛先ごと・IP ごと)があるので、1本ずつ順に送れば足りる
    private final ExecutorService executor;

    public BackgroundMail(@Value("${app.mail.background:true}") boolean background) {
        this.executor = background
                ? Executors.newSingleThreadExecutor(Thread.ofPlatform().name("background-mail").daemon().factory())
                : null;
    }

    public void run(Runnable task) {
        Runnable guarded = () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                // 宛先はログに出さない。送れなかった理由はメールを送る側がログに出している
                log.warn("メールを送れませんでした: {}", e.getMessage());
            }
        };
        if (executor == null) {
            guarded.run();
        } else {
            executor.execute(guarded);
        }
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
