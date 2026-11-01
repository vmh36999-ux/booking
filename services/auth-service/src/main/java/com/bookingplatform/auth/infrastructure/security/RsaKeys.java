package com.bookingplatform.auth.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

public final class RsaKeys {
    private RsaKeys() {
    }

    /**
     * Generates a new key pair. For dev environment only: a different key is
     * generated on every startup
     */
    public static RSAKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return build((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());

        } catch (GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException("Unable to generate RSA key pair", e);
        }

    }

    /**
     * Parses a PEM PKCS#8 private key, derives the public key, and normalizes
     * literal "\\n"
     * characters commonly introduced by environment variables.
     */
    public static RSAKey fromPem(String pem) {
        try {
            String base64 = pem
                    .replace("\\n", "\n")
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
                throw new IllegalArgumentException("Key is not RSA Private Crt Key");
            }
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            return build(publicKey, crt);
        } catch (GeneralSecurityException | JOSEException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "AUTH_RSA_PRIVATE_KEY is invalid. It must be in PEM PKCS#8 format (BEGIN PRIVATE KEY), not BEGIN RSA PRIVATE KEY.",
                    e);
        }
    }

    private static RSAKey build(RSAPublicKey publicKey, RSAPrivateKey privateKey) throws JOSEException {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint()
                .build();
    }
}
