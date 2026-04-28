package com.workflow.demo.auth.security;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.workflow.demo.domain.enums.RoleEnum;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:mySecretKeyForJWTTokenGenerationAndValidationPurposesOnlyDoNotUseInProduction123456}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs; // 24 horas por defecto

    @Value("${jwt.refresh-expiration:604800000}")
    private long jwtRefreshExpirationMs; // 7 días por defecto

    public String generateAccessToken(String userId, String email, String roleName) {
        return generateAccessToken(userId, email, roleName, jwtExpirationMs);
    }

    /**
     * Genera token de acceso con authorities en los claims
     */
    private String generateAccessToken(String userId, String email, String roleName, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        // Convertir roleName a RoleEnum y obtener authority
        RoleEnum roleEnum = RoleEnum.fromDisplayName(roleName != null ? roleName : "Cliente");
        
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("roleName", roleName)
            .claim("authorities", List.of(roleEnum.getAuthority()))  // ✅ Agregar authorities como lista
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS512)
            .compact();
    }

    public String generateRefreshToken(String userId, String email) {
        return generateToken(userId, email, null, jwtRefreshExpirationMs);
    }

    private String generateToken(String userId, String email, String roleName, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("roleName", roleName)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS512)
            .compact();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("email", String.class);
    }

    public String getRoleNameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("roleName", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("authorities", List.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public long getExpirationTimeMs() {
        return jwtExpirationMs;
    }
}
