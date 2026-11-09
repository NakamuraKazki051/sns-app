package com.snsapp.api.block;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** muter が muted をミュートしている(F-20)。muted には分からない */
@Entity
@Table(name = "mutes")
public class Mute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String muterId;

    @Column(nullable = false, length = 36)
    private String mutedId;

    @Column(nullable = false)
    private Instant createdAt;

    protected Mute() {
    }

    public Mute(String muterId, String mutedId) {
        this.muterId = muterId;
        this.mutedId = mutedId;
        this.createdAt = Instant.now();
    }
}
