//! RISC Zero guest: competition ranking commitment verifier.
//!
//! Proves:
//!   1. For every entry i: SHA-256(score_le64 ‖ salt_32) == committed_hash[i]
//!   2. The ranking is correctly sorted by score descending (ties: user_id asc)
//!
//! Uses risc0_zkvm's built-in SHA-256 accelerator (much faster than sha2 crate in zkVM).

#![no_main]

use risc0_zkvm::guest::env;
use risc0_zkvm::sha::{Impl, Sha256, Digest};
use serde::{Deserialize, Serialize};

risc0_zkvm::guest::entry!(main);

#[derive(Deserialize)]
struct Entry {
    user_id: u64,
    score:   u64,
    salt:    [u8; 32],
}

#[derive(Serialize)]
struct RankingJournal {
    competition_id:   u64,
    committed_hashes: Vec<[u8; 32]>,
    ranking:          Vec<u64>,
}

fn main() {
    let entries:         Vec<Entry>    = env::read();
    let competition_id:  u64           = env::read();
    let expected_hashes: Vec<[u8; 32]> = env::read();

    assert_eq!(entries.len(), expected_hashes.len(), "entry/hash count mismatch");

    let mut computed_hashes: Vec<[u8; 32]> = Vec::with_capacity(entries.len());

    for (i, entry) in entries.iter().enumerate() {
        // SHA-256(score_le64 ‖ salt_32)
        let mut preimage = [0u8; 40]; // 8 + 32
        preimage[..8].copy_from_slice(&entry.score.to_le_bytes());
        preimage[8..].copy_from_slice(&entry.salt);

        let digest: Digest = *Impl::hash_bytes(&preimage);
        let hash: [u8; 32] = digest.as_bytes().try_into().expect("32 bytes");

        assert_eq!(hash, expected_hashes[i], "commitment mismatch at index {}", i);
        computed_hashes.push(hash);
    }

    // Sort: score desc, user_id asc for ties
    let mut indexed: Vec<(usize, u64, u64)> = entries
        .iter()
        .enumerate()
        .map(|(i, e)| (i, e.score, e.user_id))
        .collect();
    indexed.sort_by(|a, b| b.1.cmp(&a.1).then(a.2.cmp(&b.2)));

    let ranking: Vec<u64> = indexed.iter().map(|(i, _, _)| entries[*i].user_id).collect();

    env::commit(&RankingJournal { competition_id, committed_hashes: computed_hashes, ranking });
}
