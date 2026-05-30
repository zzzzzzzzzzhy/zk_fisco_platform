//! Host prover: reads a JSON job from stdin, generates a RISC Zero receipt, writes
//! a JSON result to stdout.
//!
//! # Input JSON (stdin)
//! ```json
//! {
//!   "competitionId": 42,
//!   "entries": [
//!     { "userId": 1, "score": 9200, "saltHex": "aabbcc..." },
//!     { "userId": 2, "score": 8500, "saltHex": "ddeeff..." }
//!   ],
//!   "committedHashes": ["aabbcc...", "ddeeff..."]
//! }
//! ```
//!
//! # Output JSON (stdout)
//! ```json
//! {
//!   "imageId": "0x...",
//!   "journalHex": "...",
//!   "sealHex":    "...",
//!   "ranking":    [1, 2]
//! }
//! ```

use anyhow::{bail, Context, Result};
use methods::{RANKING_VERIFY_ELF, RANKING_VERIFY_ID};
use risc0_zkvm::{default_prover, ExecutorEnv};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::io::{self, Read};

// ── input types ────────────────────────────────────────────────────────────────

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct InputEntry {
    user_id:  u64,
    score:    u64,
    salt_hex: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProveJob {
    competition_id:   u64,
    entries:          Vec<InputEntry>,
    committed_hashes: Vec<String>, // hex-encoded [u8;32]
}

// ── types shared with guest (must match guest's Deserialize structs) ────────────

#[derive(Serialize)]
struct GuestEntry {
    user_id: u64,
    score:   u64,
    salt:    [u8; 32],
}

// ── output type ────────────────────────────────────────────────────────────────

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ProveOutput {
    image_id:    String,
    journal_hex: String,
    seal_hex:    String,
    ranking:     Vec<u64>,
}

fn main() -> Result<()> {
    let mut input = String::new();
    io::stdin().read_to_string(&mut input)?;
    let job: ProveJob = serde_json::from_str(&input).context("parse input JSON")?;

    // Decode entries
    let guest_entries: Vec<GuestEntry> = job
        .entries
        .iter()
        .map(|e| {
            let salt_bytes = hex::decode(&e.salt_hex).context("decode salt hex")?;
            if salt_bytes.len() != 32 {
                bail!("salt must be 32 bytes, got {}", salt_bytes.len());
            }
            let mut salt = [0u8; 32];
            salt.copy_from_slice(&salt_bytes);
            Ok(GuestEntry { user_id: e.user_id, score: e.score, salt })
        })
        .collect::<Result<Vec<_>>>()?;

    // Decode committed hashes
    let committed_hashes: Vec<[u8; 32]> = job
        .committed_hashes
        .iter()
        .map(|h| {
            let bytes = hex::decode(h).context("decode commitment hex")?;
            if bytes.len() != 32 {
                bail!("hash must be 32 bytes");
            }
            let mut arr = [0u8; 32];
            arr.copy_from_slice(&bytes);
            Ok(arr)
        })
        .collect::<Result<Vec<_>>>()?;

    // Build executor environment
    let env = ExecutorEnv::builder()
        .write(&guest_entries)?
        .write(&job.competition_id)?
        .write(&committed_hashes)?
        .build()?;

    // Prove
    let prover  = default_prover();
    let receipt = prover.prove(env, RANKING_VERIFY_ELF)?.receipt;

    // Decode journal to extract ranking
    #[derive(serde::Deserialize)]
    struct RankingJournal {
        competition_id:   u64,
        committed_hashes: Vec<[u8; 32]>,
        ranking:          Vec<u64>,
    }
    let journal: RankingJournal = receipt.journal.decode()?;

    let journal_hex = hex::encode(receipt.journal.bytes);
    let seal_hex    = hex::encode(bincode::serialize(&receipt.inner)?);
    let image_id    = format!("0x{}", hex::encode(RANKING_VERIFY_ID));

    let out = ProveOutput {
        image_id,
        journal_hex,
        seal_hex,
        ranking: journal.ranking,
    };
    println!("{}", serde_json::to_string(&out)?);
    Ok(())
}

// ── helper: compute commitment (mirrors the contract + guest) ──────────────────

/// Compute SHA-256(score_le64 ‖ salt).  Useful for testing.
#[allow(dead_code)]
fn compute_commitment(score: u64, salt: &[u8; 32]) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(score.to_le_bytes());
    h.update(salt);
    h.finalize().into()
}
