//! Standalone verifier: reads a proof JSON from stdin, verifies it.
//!
//! Checks:
//!   1. The ZK proof (seal) is valid for the embedded journal (cryptographic proof)
//!   2. The journalHex in the JSON matches the journal inside the receipt (consistency)
//!
//! Input: { "imageId": "0x...", "journalHex": "...", "sealHex": "..." }

use anyhow::{bail, Context, Result};
use methods::RANKING_VERIFY_ID;
use risc0_zkvm::Receipt;
use serde::Deserialize;
use std::io::{self, Read};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VerifyInput {
    journal_hex: String,
    seal_hex:    String,
}

fn main() -> Result<()> {
    let mut input = String::new();
    io::stdin().read_to_string(&mut input)?;
    let vi: VerifyInput = serde_json::from_str(&input).context("parse input JSON")?;

    let seal_bytes    = hex::decode(&vi.seal_hex).context("decode seal")?;
    let journal_bytes = hex::decode(&vi.journal_hex).context("decode journal")?;

    let receipt: Receipt = bincode::deserialize(&seal_bytes).context("deserialize receipt")?;

    // Check 1: journal bytes in JSON match the receipt's journal
    if receipt.journal.bytes != journal_bytes {
        bail!("journal mismatch: journalHex does not match receipt journal");
    }

    // Check 2: ZK proof is valid for IMAGE_ID + journal
    receipt.verify(RANKING_VERIFY_ID).context("ZK proof verification failed")?;

    println!("{{\"verified\": true}}");
    Ok(())
}
