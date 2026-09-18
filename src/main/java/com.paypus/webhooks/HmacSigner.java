package com.paypus.webhooks;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HmacSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private HmacSigner() {
    }

    public static String sign(String payload, String secret, long timestampSeconds) {
        String signedPayload = timestampSeconds + "." + payload;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);

            byte[] signatureBytes = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String signature = HexFormat.of().formatHex(signatureBytes);

            return "t=" + timestampSeconds + ",v1=" + signature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }
}