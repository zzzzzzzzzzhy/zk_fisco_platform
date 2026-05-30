package com.wereen.competitionplatform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Mock implementation of ZK rollup proof generation for local Hardhat development.
 *
 * <p>Activated automatically when {@code reward.rollup.prover-cmd} is absent or blank,
 * which is the default in a local dev environment without the real rollup-prove binary.
 *
 * <p><b>WARNING:</b> This service produces a fake 64-byte all-zero Groth16 seal that
 * is accepted by {@code MockRiscZeroVerifier} but is NOT a valid cryptographic proof.
 * Do NOT use in production or against a real {@code IRiscZeroVerifier} deployment.
 */
@Slf4j
@Service
@ConditionalOnProperty(
        name = "reward.rollup.prover-cmd",
        havingValue = "",
        matchIfMissing = true
)
public class MockRewardProofGeneratorService {

    /**
     * Groth16 seal size expected by the contract ABI: 8 × uint256 = 256 bytes.
     * Using 64 bytes of zeros as a compact placeholder that satisfies length checks
     * in the mock verifier (which ignores the seal content entirely).
     */
    private static final int MOCK_SEAL_BYTES = 64;

    /**
     * Generates a mock ZK proof file and writes it to {@code proofPath}.
     *
     * <p>The generated file contains {@value MOCK_SEAL_BYTES} zero bytes, serving as a
     * Groth16 seal placeholder accepted by {@code MockRiscZeroVerifier}.
     *
     * @param metadata  rollup batch metadata (logged for traceability, not used in proof)
     * @param proofPath destination path where the mock proof bytes will be written
     * @return {@code true} always (indicates successful mock generation)
     */
    public boolean generateMockProof(Map<String, Object> metadata, Path proofPath) {
        log.info("[MOCK] Starting mock ZK proof generation — real prover-cmd not configured");
        log.info("[MOCK] Batch metadata: {}", metadata);

        try {
            Files.createDirectories(proofPath.getParent());

            byte[] mockSeal = new byte[MOCK_SEAL_BYTES]; // all zeros by default
            Files.write(proofPath, mockSeal);

            log.info("[MOCK] Mock proof written ({} zero bytes) to: {}", MOCK_SEAL_BYTES, proofPath);
            log.info("[MOCK] This proof is valid ONLY against MockRiscZeroVerifier on local Hardhat");
            return true;

        } catch (Exception e) {
            log.error("[MOCK] Failed to write mock proof to {}: {}", proofPath, e.getMessage(), e);
            return false;
        }
    }
}
