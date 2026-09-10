package com.paypus.idempotency;

import com.paypus.tenant.Tenant;
import com.paypus.tenant.TenantRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TenantRepository tenantRepository;

    public IdempotencyService(
            IdempotencyKeyRepository idempotencyKeyRepository,
            TenantRepository tenantRepository
    ) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult begin(UUID tenantId, String idempotencyKey, String requestBody) {
        String requestHash = hash(requestBody);

        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();

            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used with a different request payload: " + idempotencyKey
                );
            }

            if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                throw new IdempotencyConflictException(
                        "Request with this idempotency key is already being processed: " + idempotencyKey
                );
            }

            return new IdempotencyResult(true, record.getResponseStatus(), record.getResponseBody());
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        IdempotencyKey record = new IdempotencyKey();
        record.setTenant(tenant);
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        record.setCreatedAt(OffsetDateTime.now());

        try {
            idempotencyKeyRepository.save(record);
            idempotencyKeyRepository.flush();
        } catch (DataIntegrityViolationException e) {
            e.printStackTrace();
            throw new IdempotencyConflictException(
                    "Concurrent request with the same idempotency key: " + idempotencyKey
            );
        }

        return new IdempotencyResult(false, null, null);
    }

    @Transactional
    public void complete(UUID tenantId, String idempotencyKey, int responseStatus, String responseBody) {
        IdempotencyKey record = idempotencyKeyRepository
                .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "No idempotency record found to complete: " + idempotencyKey
                ));

        record.setResponseStatus(responseStatus);
        record.setResponseBody(responseBody);
        record.setStatus(IdempotencyStatus.COMPLETED);

        idempotencyKeyRepository.save(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID tenantId, String idempotencyKey) {
        idempotencyKeyRepository
                .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .ifPresent(idempotencyKeyRepository::delete);
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}