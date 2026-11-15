package com.nexum.service;

import com.nexum.security.UsuarioDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-minutes:30}") long expirationMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
    }

    public String generarToken(UsuarioDetails usuarioDetails) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expirationMinutes * 60);
        List<String> authorities = usuarioDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(usuarioDetails.getUsername())
                .claim("uid", usuarioDetails.getId().toString())
                .claim("pv", versionPassword(usuarioDetails))
                .claim("authorities", authorities)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String extraerEmail(String token) {
        return extraerClaims(token).getSubject();
    }

    public boolean esValido(String token, UsuarioDetails usuarioDetails) {
        return usuarioDetails.getUsername().equals(extraerEmail(token))
                && versionPassword(usuarioDetails).equals(extraerClaims(token).get("pv", String.class))
                && extraerClaims(token).getExpiration().after(new Date());
    }

    private String versionPassword(UsuarioDetails usuario) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(usuario.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }

    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
