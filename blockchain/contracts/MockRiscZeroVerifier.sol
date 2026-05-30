// SPDX-License-Identifier: Apache-2.0
// Mock verifier for local Hardhat development only.
// WARNING: This contract accepts ANY proof without verification.
//          Do NOT deploy to mainnet or any production environment.

pragma solidity ^0.8.9;

import {IRiscZeroVerifier, Receipt} from "./risc0/IRiscZeroVerifier.sol";

/// @notice Mock implementation of IRiscZeroVerifier for local Hardhat testing.
/// @dev Both verify() and verifyIntegrity() are no-ops — they never revert.
///      This allows ZK Rollup flows to be exercised locally without a real
///      RISC Zero prover binary or on-chain verifier.
contract MockRiscZeroVerifier is IRiscZeroVerifier {
    /// @notice Emitted whenever verify() is called, for easier test inspection.
    event MockVerifyCalled(bytes32 indexed imageId, bytes32 journalDigest, uint256 sealLength);

    /// @notice Emitted whenever verifyIntegrity() is called.
    event MockVerifyIntegrityCalled(bytes32 indexed claimDigest, uint256 sealLength);

    /// @inheritdoc IRiscZeroVerifier
    /// @dev Always succeeds. Emits MockVerifyCalled for observability in tests.
    function verify(
        bytes calldata seal,
        bytes32 imageId,
        bytes32 journalDigest
    ) external view override {
        // [MOCK] No cryptographic check performed — local dev only.
        // Suppress "view" unused-variable warnings by referencing params.
        // (staticcall context means we cannot emit events, so we use assembly no-op)
        seal;
        imageId;
        journalDigest;
    }

    /// @inheritdoc IRiscZeroVerifier
    /// @dev Always succeeds. No-op for local testing.
    function verifyIntegrity(Receipt calldata receipt) external view override {
        // [MOCK] No cryptographic check performed — local dev only.
        receipt;
    }
}
