//! Standalone verifier: reads a proof JSON from stdin, verifies it, prints result.
//!
//! # Input JSON (stdin)
//! Same shape as ProveOutput from prove.rs:
//! { "imageId": "0x...", "journalHex": "...", "sealHex": "..." }

use anyhow::{Context, Result};
use methods::RANKING_VERIFY_ID;
use risc0_zkvm::{Receipt, InnerReceipt};
use serde::Deserialize;
use std::io::{self, Read};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VerifyInput {
    image_id:    String,
    journal_hex: String,
    seal_hex:    String,
}

fn main() -> Result<()> {
    let mut input = String::new();
    io::stdin().read_to_string(&mut input)?;
    let vi: VerifyInput = serde_json::from_str(&input).context("parse input JSON")?;

    let journal_bytes = hex::decode(&vi.journal_hex).context("decode journal")?;
    let seal_bytes    = hex::decode(&vi.seal_hex).context("decode seal")?;
    let inner: InnerReceipt = bincode::deserialize(&seal_bytes).context("deserialize receipt")?;

    let receipt = Receipt::new(inner, journal_bytes);
    receipt.verify(RANKING_VERIFY_ID).context("proof verification failed")?;

    println!("{{\"verified\": true}}");
    Ok(())
}
