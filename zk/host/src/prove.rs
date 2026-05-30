//! Host prover: reads a JSON job from stdin, generates a RISC Zero receipt,
//! writes a JSON result to stdout.
//!
//! # Input JSON (stdin)
//! ```json
//! {
//!   "competitionId": 42,
//!   "entries": [
//!     { "userId": 1, "score": 9200, "saltHex": "aabb..." },
//!     { "userId": 2, "score": 8500, "saltHex": "ccdd..." }
//!   ],
//!   "committedHashes": ["aabb...", "ccdd..."]
//! }
//! ```
//!
//! # Output JSON (stdout)
//! ```json
//! {
//!   "imageId": "0x...",
//!   "journalHex": "...",
//!   "sealHex": "...",
//!   "ranking": [1, 2]
//! }
//! ```

use anyhow::{bail, Context, Result};
use methods::{RANKING_VERIFY_ELF, RANKING_VERIFY_ID};
use risc0_zkvm::{default_prover, ExecutorEnv};
use serde::{Deserialize, Serialize};
use std::io::{self, Read};

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
    committed_hashes: Vec<String>,
}

// Must mirror the guest's Entry struct exactly
#[derive(Serialize)]
struct GuestEntry {
    user_id: u64,
    score:   u64,
    salt:    [u8; 32],
}

// Mirrors guest RankingJournal
#[derive(Deserialize)]
struct RankingJournal {
    #[allow(dead_code)]
    competition_id:   u64,
    #[allow(dead_code)]
    committed_hashes: Vec<[u8; 32]>,
    ranking:          Vec<u64>,
}

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
        .collect::<Result<_>>()?;

    let committed_hashes: Vec<[u8; 32]> = job
        .committed_hashes
        .iter()
        .map(|h| {
            let bytes = hex::decode(h).context("decode hash hex")?;
            if bytes.len() != 32 { bail!("hash must be 32 bytes"); }
            let mut arr = [0u8; 32];
            arr.copy_from_slice(&bytes);
            Ok(arr)
        })
        .collect::<Result<_>>()?;

    let env = ExecutorEnv::builder()
        .write(&guest_entries).unwrap()
        .write(&job.competition_id).unwrap()
        .write(&committed_hashes).unwrap()
        .build().unwrap();

    let prover     = default_prover();
    let prove_info = prover.prove(env, RANKING_VERIFY_ELF)?;
    let receipt    = prove_info.receipt;

    let journal: RankingJournal = receipt.journal.decode()?;
    let journal_hex = hex::encode(&receipt.journal.bytes);
    let seal_hex    = hex::encode(bincode::serialize(&receipt).context("serialize receipt")?);
    let image_id    = format!("0x{}", hex::encode(
        RANKING_VERIFY_ID.iter().flat_map(|w| w.to_le_bytes()).collect::<Vec<_>>()
    ));

    let out = ProveOutput { image_id, journal_hex, seal_hex, ranking: journal.ranking };
    println!("{}", serde_json::to_string(&out)?);
    Ok(())
}
