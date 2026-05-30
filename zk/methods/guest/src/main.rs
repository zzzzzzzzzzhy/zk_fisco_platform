//! RISC Zero guest program: competition ranking commitment verifier.
//!
//! # What this circuit proves
//!
//! The organizer pre-committed each participant's score via:
//!   commitment_i = SHA-256(score_i_le64 ‖ salt_i_32bytes)
//! and published those hashes on-chain before results were announced.
//!
//! This guest proves:
//!   1. For every entry i: SHA-256(score_i ‖ salt_i) == committed_hash_i
//!   2. The final ranking is derived by sorting entries by score descending
//!      (ties broken by user_id ascending – deterministic)
//!
//! # Private inputs  (not visible in the proof)
//!   Vec<Entry { user_id, score, salt: [u8;32] }>
//!
//! # Public inputs  (bound to the proof via journal)
//!   competition_id: u64
//!   committed_hashes: Vec<[u8;32]>   – one per entry, same order as entries
//!
//! # Journal (public outputs committed on-chain)
//!   RankingJournal { competition_id, committed_hashes, ranking: Vec<u64> }

#![no_main]

use risc0_zkvm::guest::env;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

risc0_zkvm::guest::entry!(main);

#[derive(Deserialize)]
struct Entry {
    user_id: u64,
    score:   u64,
    salt:    [u8; 32],
}

/// Written to the journal; becomes the on-chain public output.
#[derive(Serialize)]
struct RankingJournal {
    competition_id:   u64,
    /// Each hash corresponds to entries[i] in submission order.
    committed_hashes: Vec<[u8; 32]>,
    /// user_ids sorted from rank-1 (highest score) to rank-N.
    ranking:          Vec<u64>,
}

fn main() {
    // --- read private inputs ---
    let entries:          Vec<Entry>    = env::read();
    // --- read public inputs (also bound into journal so verifier can check) ---
    let competition_id:   u64           = env::read();
    let expected_hashes:  Vec<[u8; 32]> = env::read();

    assert_eq!(
        entries.len(),
        expected_hashes.len(),
        "entry count must match committed_hashes count"
    );

    // --- constraint 1: verify each commitment ---
    let mut computed_hashes: Vec<[u8; 32]> = Vec::with_capacity(entries.len());
    for (i, entry) in entries.iter().enumerate() {
        let mut h = Sha256::new();
        h.update(entry.score.to_le_bytes());
        h.update(entry.salt);
        let computed: [u8; 32] = h.finalize().into();

        assert_eq!(
            computed, expected_hashes[i],
            "commitment mismatch at index {}",
            i
        );
        computed_hashes.push(computed);
    }

    // --- constraint 2: derive ranking (score desc, user_id asc for ties) ---
    let mut indexed: Vec<(usize, u64, u64)> = entries
        .iter()
        .enumerate()
        .map(|(i, e)| (i, e.score, e.user_id))
        .collect();
    indexed.sort_by(|a, b| b.1.cmp(&a.1).then(a.2.cmp(&b.2)));

    let ranking: Vec<u64> = indexed.iter().map(|(i, _, _)| entries[*i].user_id).collect();

    // --- commit journal to chain ---
    env::commit(&RankingJournal {
        competition_id,
        committed_hashes: computed_hashes,
        ranking,
    });
}
