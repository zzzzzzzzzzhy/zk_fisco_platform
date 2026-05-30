// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";

interface IRiscZeroVerifier {
    function verify(
        bytes calldata seal,
        bytes32 imageId,
        bytes32 journalDigest
    ) external view;
}

/**
 * @title ContentRollupRegistry
 * @dev Rollup 批次注册合约（仅存摘要 + 证明验证）
 */
contract ContentRollupRegistry is Ownable {
    struct Batch {
        bytes32 merkleRoot;
        uint256 count;
        uint256 windowStart;
        uint256 windowEnd;
        uint256 timestamp;
    }

    IRiscZeroVerifier public immutable verifier;
    bytes32 public immutable imageId;

    mapping(uint256 => Batch) private batches;

    event BatchSubmitted(
        uint256 indexed batchId,
        bytes32 indexed merkleRoot,
        uint256 count,
        uint256 windowStart,
        uint256 windowEnd,
        uint256 timestamp
    );

    constructor(address initialOwner, address verifierAddress, bytes32 _imageId) Ownable(initialOwner) {
        require(verifierAddress != address(0), "invalid verifier");
        verifier = IRiscZeroVerifier(verifierAddress);
        imageId = _imageId;
    }

    function submitBatch(
        bytes calldata seal,
        bytes32 journalDigest,
        uint256 batchId,
        bytes32 merkleRoot,
        uint256 count,
        uint256 windowStart,
        uint256 windowEnd
    ) external onlyOwner {
        require(merkleRoot != bytes32(0), "invalid root");
        require(count > 0, "invalid count");
        require(windowEnd > windowStart, "invalid window");
        require(batches[batchId].timestamp == 0, "batch exists");

        bytes32 expectedDigest = computeJournalDigest(
            batchId,
            merkleRoot,
            count,
            windowStart,
            windowEnd
        );
        require(journalDigest == expectedDigest, "invalid digest");
        verifier.verify(seal, imageId, journalDigest);

        batches[batchId] = Batch({
            merkleRoot: merkleRoot,
            count: count,
            windowStart: windowStart,
            windowEnd: windowEnd,
            timestamp: block.timestamp
        });

        emit BatchSubmitted(batchId, merkleRoot, count, windowStart, windowEnd, block.timestamp);
    }

    function getBatch(uint256 batchId)
        external
        view
        returns (bytes32 merkleRoot, uint256 count, uint256 windowStart, uint256 windowEnd, uint256 timestamp)
    {
        Batch memory batch = batches[batchId];
        return (batch.merkleRoot, batch.count, batch.windowStart, batch.windowEnd, batch.timestamp);
    }

    function isBatchSubmitted(uint256 batchId) external view returns (bool) {
        return batches[batchId].timestamp != 0;
    }

    function computeJournalDigest(
        uint256 batchId,
        bytes32 merkleRoot,
        uint256 count,
        uint256 windowStart,
        uint256 windowEnd
    ) internal pure returns (bytes32) {
        bytes memory journal = new bytes(640);
        _writeWord(journal, 0, bytes32(batchId));
        _writeWord(journal, 128, merkleRoot);
        _writeWord(journal, 256, bytes32(count));
        _writeWord(journal, 384, bytes32(windowStart));
        _writeWord(journal, 512, bytes32(windowEnd));
        return sha256(journal);
    }

    function _writeWord(bytes memory out, uint256 base, bytes32 word) internal pure {
        unchecked {
            for (uint256 i = 0; i < 32; i++) {
                out[base + i * 4] = word[i];
            }
        }
    }
}
