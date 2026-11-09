package com.snsapp.api.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * ログインの証明(JWT)を作り、届いたものを確かめる。中身は「誰か(sub)」「どのログインか(sid)」「世代(ver)」
 * 「いつまで有効か(exp)」。sid と ver で、期限前でも無効にできる(CurrentUser、docs/auth.md)
 */
@Component
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    // HS256 の鍵は256ビット(32バイト)以上
    static final int MIN_SECRET_BYTES = 32;

    private static final String SESSION_CLAIM = "sid";
    private static final String VERSION_CLAIM = "ver";

    public record Verified(String userId, String sessionId, int version, Instant issuedAt) {
    }

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final Duration ttl;
    private Clock clock = Clock.systemUTC();

    public TokenService(@Value("${app.auth.jwt-secret}") String secret,
                        @Value("${app.auth.token-ttl}") Duration ttl) {
        byte[] key;
        if (secret.isBlank()) {
            key = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(key);
            log.warn("JWT_SECRET が空なので、署名の鍵をその場で作りました。再起動するとログアウトになります");
        } else {
            key = secret.getBytes(StandardCharsets.UTF_8);
            if (key.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET は" + MIN_SECRET_BYTES + "バイト以上にしてください");
            }
        }
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT の鍵を用意できません", e);
        }
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    /** 新しいログインの ID。作り直した JWT にも同じものを入れる */
    public static String newSessionId() {
        return UUID.randomUUID().toString();
    }

    public String issue(String userId, String sessionId, int version) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim(SESSION_CLAIM, sessionId)
                .claim(VERSION_CLAIM, version)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT に署名できません", e);
        }
        return jwt.serialize();
    }

    /** 署名が正しく、期限内なら、誰のものかを返す。壊れている・改ざん・期限切れは空 */
    public Optional<Verified> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            // 「署名なし(none)」や別の方式を名乗る JWT は受け付けない
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiresAt = claims.getExpirationTime();
            Date issuedAt = claims.getIssueTime();
            String sessionId = claims.getStringClaim(SESSION_CLAIM);
            Integer version = claims.getIntegerClaim(VERSION_CLAIM);
            if (claims.getSubject() == null || sessionId == null || version == null
                    || expiresAt == null || issuedAt == null
                    || !clock.instant().isBefore(expiresAt.toInstant())) {
                return Optional.empty();
            }
            return Optional.of(new Verified(claims.getSubject(), sessionId, version, issuedAt.toInstant()));
        } catch (ParseException | JOSEException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    Instant now() {
        return clock.instant();
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
