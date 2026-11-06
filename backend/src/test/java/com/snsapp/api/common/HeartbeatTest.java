package com.snsapp.api.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 監視(CloudWatch)は、この INFO のログが届かなくなったらアプリが止まったと判断する */
class HeartbeatTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(Heartbeat.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void capture() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void release() {
        logger.detachAppender(logs);
    }

    @Test
    void reportsThatItIsAliveWhenTheDatabaseAnswers() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:heartbeat");

        new Heartbeat(dataSource).beat();

        assertEquals(1, logs.list.size());
        assertEquals(Level.INFO, logs.list.get(0).getLevel());
    }

    @Test
    void reportsAnErrorInsteadWhenTheDatabaseIsDown() {
        new Heartbeat(new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("password=secret");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }
        }).beat();

        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertFalse(event.getFormattedMessage().contains("secret"));
    }
}
