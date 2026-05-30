package com.wereen.competitionplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.SubmissionCommitmentMapper;
import com.wereen.competitionplatform.mapper.ZkRankingProofMapper;
import com.wereen.competitionplatform.model.entity.SubmissionCommitment;
import com.wereen.competitionplatform.model.entity.ZkRankingProof;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 竞赛排名 ZK 证明核心服务。
 *
 * <p>完整链路：
 * <ol>
 *   <li>organizer 评分完成后调用 {@link #commitScores} — 生成每位参赛者的
 *       SHA-256(score_le64 ‖ salt) 承诺并保存</li>
 *   <li>调用 {@link #generateRankingProof} — 调用外部 prove 二进制（或 Mock）
 *       生成 ZK 证明，写入 {@code zk_ranking_proofs}</li>
 *   <li>前端/合约读取 {@link #getProof} 获取可验证排名</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZkRankingService {

    private final SubmissionCommitmentMapper commitmentMapper;
    private final ZkRankingProofMapper       proofMapper;
    private final ObjectMapper               objectMapper;

    /**
     * Path to the compiled {@code prove} binary from {@code zk/host}.
     * Leave blank (default) to use the built-in mock prover.
     */
    @Value("${zk.ranking.prover-binary:}")
    private String proverBinary;

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Commit scores for all participants of a competition.
     *
     * <p>For each (userId, score) pair a random 32-byte salt is generated and
     * commitment = SHA-256(score_le64 ‖ salt) is stored.  The commitments are
     * the values that should be pushed on-chain via {@code CompetitionCommitRegistry.commitScores()}.
     *
     * @param competitionId the competition
     * @param scoreMap      userId → score (integer, ×100 for 2 decimal places)
     * @return list of stored commitments (salt is included so they can be revealed later)
     */
    @Transactional
    public List<SubmissionCommitment> commitScores(Long competitionId, Map<Long, Long> scoreMap) {
        if (scoreMap.isEmpty()) throw new BusinessException("得分数据为空");

        List<SubmissionCommitment> result = new ArrayList<>();
        SecureRandom rng = new SecureRandom();

        for (Map.Entry<Long, Long> entry : scoreMap.entrySet()) {
            Long userId = entry.getKey();
            Long score  = entry.getValue();

            // Check duplicate
            SubmissionCommitment existing = commitmentMapper.findByCompetitionAndUser(competitionId, userId);
            if (existing != null) {
                result.add(existing);
                continue;
            }

            byte[] salt = new byte[32];
            rng.nextBytes(salt);

            byte[] commitBytes = sha256(score, salt);

            SubmissionCommitment c = new SubmissionCommitment();
            c.setCompetitionId(competitionId);
            c.setUserId(userId);
            c.setCommitmentHash(hex(commitBytes));
            c.setSaltHex(hex(salt));
            c.setScore(score);
            c.setRevealed(0);
            commitmentMapper.insert(c);
            result.add(c);
        }
        log.info("[ZK] committed {} scores for competition {}", result.size(), competitionId);
        return result;
    }

    /**
     * Generate (or mock) a ZK proof of correct ranking for this competition.
     *
     * <p>Requires that all participants' scores have been committed via {@link #commitScores}.
     * After this call the commitments are marked as revealed.
     */
    @Transactional
    public ZkRankingProof generateRankingProof(Long competitionId) throws Exception {
        List<SubmissionCommitment> commits = commitmentMapper.findByCompetitionId(competitionId);
        if (commits.isEmpty()) throw new BusinessException("该竞赛暂无承诺记录，请先调用 commitScores");

        ZkRankingProof proof;
        if (proverBinary != null && !proverBinary.isBlank()) {
            proof = proveWithBinary(competitionId, commits);
        } else {
            proof = mockProve(competitionId, commits);
        }

        // Mark all commitments as revealed
        LocalDateTime now = LocalDateTime.now();
        for (SubmissionCommitment c : commits) {
            c.setRevealed(1);
            c.setRevealedAt(now);
            commitmentMapper.updateById(c);
        }

        proofMapper.insert(proof);
        log.info("[ZK] proof generated for competition {}, status={}", competitionId, proof.getStatus());
        return proof;
    }

    /** Get the latest proof for a competition, or null if none exists. */
    public ZkRankingProof getProof(Long competitionId) {
        return proofMapper.findLatestByCompetitionId(competitionId);
    }

    /** Get the commitment record for a specific participant (for self-verification). */
    public SubmissionCommitment getCommitment(Long competitionId, Long userId) {
        return commitmentMapper.findByCompetitionAndUser(competitionId, userId);
    }

    /** Get all commitments for a competition (public hashes only, salts stripped). */
    public List<Map<String, Object>> getPublicCommitments(Long competitionId) {
        return commitmentMapper.findByCompetitionId(competitionId).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId",         c.getUserId());
                    m.put("commitmentHash", c.getCommitmentHash());
                    m.put("revealed",       c.getRevealed());
                    if (c.getRevealed() == 1) {
                        m.put("score", c.getScore());
                    }
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── proof generation ──────────────────────────────────────────────────────

    /**
     * Mock prover: verifies commitments in Java, derives ranking, produces a
     * deterministic placeholder proof.  Works without the Rust toolchain.
     */
    private ZkRankingProof mockProve(Long competitionId, List<SubmissionCommitment> commits) {
        // Verify each commitment
        for (SubmissionCommitment c : commits) {
            byte[] salt         = unhex(c.getSaltHex());
            byte[] expected     = unhex(c.getCommitmentHash());
            byte[] recomputed   = sha256(c.getScore(), salt);
            if (!Arrays.equals(expected, recomputed)) {
                throw new BusinessException("承诺验证失败 userId=" + c.getUserId());
            }
        }

        // Sort by score desc, userId asc
        List<SubmissionCommitment> sorted = commits.stream()
                .sorted(Comparator.comparingLong(SubmissionCommitment::getScore).reversed()
                        .thenComparingLong(SubmissionCommitment::getUserId))
                .collect(Collectors.toList());

        List<Long> ranking = sorted.stream().map(SubmissionCommitment::getUserId).collect(Collectors.toList());

        // Build mock journal bytes (mirrors guest RankingJournal encoding)
        byte[] journal = encodeJournal(competitionId, commits, ranking);
        byte[] digest  = sha256Bytes(journal);

        String rankingJson;
        try {
            rankingJson = objectMapper.writeValueAsString(ranking);
        } catch (JsonProcessingException e) {
            rankingJson = ranking.toString();
        }

        ZkRankingProof proof = new ZkRankingProof();
        proof.setCompetitionId(competitionId);
        proof.setImageId("mock-image-id");
        proof.setSealHex(hex(new byte[64]));          // 64 zero bytes – accepted by MockRiscZeroVerifier
        proof.setJournalHex(hex(journal));
        proof.setJournalDigest(hex(digest));
        proof.setRankingJson(rankingJson);
        proof.setStatus("MOCK");
        return proof;
    }

    /**
     * Real prover: spawns {@code proverBinary} as a subprocess, passes JSON on stdin,
     * reads JSON result from stdout.
     */
    private ZkRankingProof proveWithBinary(Long competitionId, List<SubmissionCommitment> commits)
            throws Exception {
        // Build input JSON
        List<Map<String, Object>> entries = commits.stream().map(c -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("userId",  c.getUserId());
            e.put("score",   c.getScore());
            e.put("saltHex", c.getSaltHex());
            return e;
        }).collect(Collectors.toList());

        List<String> hashes = commits.stream()
                .map(SubmissionCommitment::getCommitmentHash)
                .collect(Collectors.toList());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("competitionId",   competitionId);
        input.put("entries",         entries);
        input.put("committedHashes", hashes);

        String inputJson = objectMapper.writeValueAsString(input);

        Process proc = new ProcessBuilder(proverBinary)
                .redirectErrorStream(true)
                .start();
        proc.getOutputStream().write(inputJson.getBytes());
        proc.getOutputStream().close();

        String output = new String(proc.getInputStream().readAllBytes());
        int exitCode  = proc.waitFor();
        if (exitCode != 0) throw new BusinessException("ZK prover 失败: " + output);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(output, Map.class);

        ZkRankingProof proof = new ZkRankingProof();
        proof.setCompetitionId(competitionId);
        proof.setImageId((String) result.get("imageId"));
        proof.setSealHex((String) result.get("sealHex"));
        proof.setJournalHex((String) result.get("journalHex"));

        byte[] journalBytes = unhex((String) result.get("journalHex"));
        proof.setJournalDigest(hex(sha256Bytes(journalBytes)));

        proof.setRankingJson(objectMapper.writeValueAsString(result.get("ranking")));
        proof.setStatus("REAL");
        return proof;
    }

    // ── encoding helpers ─────────────────────────────────────────────────────

    /** SHA-256(score_le64 ‖ salt_32) */
    private byte[] sha256(long score, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(score);
            md.update(buf.array());
            md.update(salt);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] sha256Bytes(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Encode journal matching the guest's borsh-like output so the on-chain digest check passes.
     * Layout: competitionId(le64) + hashes_len(le32) + hashes[] + ranking_len(le32) + ranking[](le64)
     */
    private byte[] encodeJournal(Long competitionId, List<SubmissionCommitment> commits, List<Long> ranking) {
        int n   = commits.size();
        int m   = ranking.size();
        int len = 8 + 4 + 32 * n + 4 + 8 * m;
        ByteBuffer buf = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);

        buf.putLong(competitionId);
        buf.putInt(n);
        for (SubmissionCommitment c : commits) {
            buf.put(unhex(c.getCommitmentHash()));
        }
        buf.putInt(m);
        for (Long uid : ranking) {
            buf.putLong(uid);
        }
        return buf.array();
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private byte[] unhex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex, i * 2, i * 2 + 2, 16);
        }
        return out;
    }
}
