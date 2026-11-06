package com.snsapp.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * アプリと DB が動いていることを、1分ごとにログで知らせる。
 * 監視(CloudWatch)は、この INFO のログが届かなくなったら止まったと判断する(docs/monitoring.md)
 */
@Component
public class Heartbeat {

    private static final Logger log = LoggerFactory.getLogger(Heartbeat.class);
    private static final int TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;

    public Heartbeat(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Scheduled(fixedRate = 60_000)
    public void beat() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(TIMEOUT_SECONDS)) {
                log.info("動いています");
            } else {
                log.error("DB が応答しません");
            }
        } catch (SQLException e) {
            // 例外の文章には接続先が入ることがあるので、種類だけ出す
            log.error("DB に接続できません: {}", e.getClass().getSimpleName());
        }
    }
}
