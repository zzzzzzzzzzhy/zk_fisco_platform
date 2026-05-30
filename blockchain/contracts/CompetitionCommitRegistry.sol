// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";

interface IRiscZeroVerifier {
    function verify(bytes calldata seal, bytes32 imageId, bytes32 journalDigest) external view;
}

/**
 * @title CompetitionCommitRegistry
 * @notice Records per-competition score commitments and accepts a ZK proof of
 *         correct ranking derived from those commitments.
 *
 * Flow
 * ────
 * 1. Organizer calls commitScores() before announcing results – stores
 *    sha256(score ‖ salt) for each participant on-chain.
 * 2. After evaluation, organizer calls submitRankingProof() with a RISC Zero
 *    Groth16 receipt proving the published ranking is consistent with the
 *    pre-committed hashes.
 * 3. Anyone can call getVerifiedRanking() to see the immutable, ZK-verified result.
 */
contract CompetitionCommitRegistry is Ownable {

    // ── state ───────────────────────────────────────────────────────────────────

    struct CommitRecord {
        uint256   competitionId;
        uint64[]  userIds;
        bytes32[] committedHashes;  // sha256(score_le64 ‖ salt_32) per user
        uint256   committedAt;
    }

    struct RankingRecord {
        uint256  competitionId;
        uint64[] ranking;           // userIds sorted best→worst
        bytes32  journalDigest;
        uint256  provenAt;
    }

    IRiscZeroVerifier public immutable verifier;
    bytes32           public immutable imageId;

    mapping(uint256 => CommitRecord)  private _commits;
    mapping(uint256 => RankingRecord) private _rankings;

    // ── events ──────────────────────────────────────────────────────────────────

    event ScoresCommitted(uint256 indexed competitionId, uint256 participantCount, uint256 committedAt);
    event RankingProven(uint256 indexed competitionId, uint64[] ranking, uint256 provenAt);

    // ── constructor ─────────────────────────────────────────────────────────────

    constructor(address initialOwner, address verifierAddress, bytes32 _imageId)
        Ownable(initialOwner)
    {
        require(verifierAddress != address(0), "invalid verifier");
        verifier = IRiscZeroVerifier(verifierAddress);
        imageId  = _imageId;
    }

    // ── organizer actions ────────────────────────────────────────────────────────

    /**
     * @notice Commit score hashes for all participants before announcing results.
     * @param competitionId  Platform competition ID.
     * @param userIds        Participant IDs in submission order.
     * @param committedHashes sha256(score_le64 ‖ salt_32) per participant.
     */
    function commitScores(
        uint256   competitionId,
        uint64[]  calldata userIds,
        bytes32[] calldata committedHashes
    ) external onlyOwner {
        require(userIds.length == committedHashes.length, "length mismatch");
        require(userIds.length > 0, "no participants");
        require(_commits[competitionId].committedAt == 0, "already committed");

        _commits[competitionId] = CommitRecord({
            competitionId:   competitionId,
            userIds:         userIds,
            committedHashes: committedHashes,
            committedAt:     block.timestamp
        });

        emit ScoresCommitted(competitionId, userIds.length, block.timestamp);
    }

    /**
     * @notice Submit a RISC Zero proof that the given ranking is consistent with
     *         the pre-committed hashes.
     * @param competitionId  Must match a prior commitScores() call.
     * @param seal           Groth16 proof bytes from the RISC Zero prover.
     * @param journalDigest  sha256 of the guest journal bytes.
     * @param ranking        userIds in rank order (rank-1 first) – must match journal.
     */
    function submitRankingProof(
        uint256         competitionId,
        bytes  calldata seal,
        bytes32         journalDigest,
        uint64[] calldata ranking
    ) external onlyOwner {
        require(_commits[competitionId].committedAt != 0, "scores not committed");
        require(_rankings[competitionId].provenAt  == 0,  "already proven");

        // Verify the ZK proof
        verifier.verify(seal, imageId, journalDigest);

        // Verify the journal digest matches the expected content
        bytes32 expectedDigest = _computeJournalDigest(
            competitionId,
            _commits[competitionId].committedHashes,
            ranking
        );
        require(journalDigest == expectedDigest, "journal digest mismatch");

        _rankings[competitionId] = RankingRecord({
            competitionId: competitionId,
            ranking:       ranking,
            journalDigest: journalDigest,
            provenAt:      block.timestamp
        });

        emit RankingProven(competitionId, ranking, block.timestamp);
    }

    // ── queries ─────────────────────────────────────────────────────────────────

    function getCommitRecord(uint256 competitionId)
        external view
        returns (uint64[] memory userIds, bytes32[] memory committedHashes, uint256 committedAt)
    {
        CommitRecord storage r = _commits[competitionId];
        return (r.userIds, r.committedHashes, r.committedAt);
    }

    function getVerifiedRanking(uint256 competitionId)
        external view
        returns (uint64[] memory ranking, bytes32 journalDigest, uint256 provenAt)
    {
        RankingRecord storage r = _rankings[competitionId];
        return (r.ranking, r.journalDigest, r.provenAt);
    }

    function isRankingProven(uint256 competitionId) external view returns (bool) {
        return _rankings[competitionId].provenAt != 0;
    }

    // ── internal ─────────────────────────────────────────────────────────────────

    /**
     * @dev Recompute the journal digest matching risc0-zkvm serde encoding.
     *
     * risc0 serde stores each u8 as one LE u32 word (32-bit RISC-V word alignment).
     * Layout:
     *   competitionId : 8 bytes  (LE u64)
     *   hashes_len    : 4 bytes  (LE u32)
     *   hashes        : n * 32 * 4 bytes  (each byte of each hash as one LE u32 word)
     *   ranking_len   : 4 bytes  (LE u32)
     *   ranking       : m * 8 bytes  (each userId as LE u64)
     */
    function _computeJournalDigest(
        uint256         competitionId,
        bytes32[] storage committedHashes,
        uint64[]  calldata ranking
    ) internal view returns (bytes32) {
        uint256 n = committedHashes.length;
        uint256 m = ranking.length;
        // each hash byte costs 4 bytes in risc0 word-per-byte encoding
        bytes memory buf = new bytes(8 + 4 + (32 * 4) * n + 4 + 8 * m);
        uint256 off = 0;

        // competitionId LE64
        for (uint256 i = 0; i < 8; i++) {
            buf[off++] = bytes1(uint8(competitionId >> (8 * i)));
        }
        // hashes length LE32
        for (uint256 i = 0; i < 4; i++) {
            buf[off++] = bytes1(uint8(n >> (8 * i)));
        }
        // hashes: each byte stored as a 4-byte LE u32 word
        for (uint256 i = 0; i < n; i++) {
            bytes32 h = committedHashes[i];
            for (uint256 j = 0; j < 32; j++) {
                buf[off++] = h[j];   // byte value
                buf[off++] = 0;      // upper 3 bytes of u32 word are zero
                buf[off++] = 0;
                buf[off++] = 0;
            }
        }
        // ranking length LE32
        for (uint256 i = 0; i < 4; i++) {
            buf[off++] = bytes1(uint8(m >> (8 * i)));
        }
        // ranking LE64 each
        for (uint256 i = 0; i < m; i++) {
            uint64 uid = ranking[i];
            for (uint256 j = 0; j < 8; j++) {
                buf[off++] = bytes1(uint8(uid >> (8 * j)));
            }
        }

        return sha256(buf);
    }
}
