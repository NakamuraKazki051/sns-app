package com.snsapp.api.block;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** blocker が blocked をブロックしている(F-20) */
@Entity
@Table(name = "blocks")
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String blockerId;

    @Column(nullable = false, length = 36)
    private String blockedId;

    @Column(nullable = false)
    private Instant createdAt;

    protected Block() {
    }

    public Block(String blockerId, String blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = Instant.now();
    }
}
