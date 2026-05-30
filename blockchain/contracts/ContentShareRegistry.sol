// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import "@openzeppelin/contracts/utils/cryptography/EIP712.sol";

/**
 * @title ContentShareRegistry
 * @dev 链上确权合约
 * 特性：
 * 1. 验证用户签名 (去中心化/真实性证明)
 * 2. Metadata 仅通过事件抛出 (节省 90% Gas)
 * 3. 采用 EIP-712 结构化签名校验，具备链 ID / 合约地址防重放能力
 */
contract ContentShareRegistry is Ownable, EIP712 {
    using ECDSA for bytes32;

    struct ShareRecord {
        address publisher;
        uint256 shareId;
        uint256 timestamp;
    }

    // 记录内容 Hash 对应的确权信息
    mapping(bytes32 => ShareRecord) private records;
    // 记录业务 ID 是否已被使用 (防撞库)
    mapping(uint256 => bool) private shareIdUsed;

    // EIP-712 结构体哈希
    // EIP712 域本身已包含 chainId 和 verifyingContract，可防跨链/跨合约重放
    bytes32 public constant SHARE_TYPEHASH =
        keccak256("ContentShare(bytes32 dataHash,address publisher,uint256 shareId,string metadata)");

    event ShareRecorded(
        bytes32 indexed dataHash,
        address indexed publisher,
        uint256 indexed shareId,
        string metadata,
        uint256 timestamp
    );

    constructor(address initialOwner) Ownable(initialOwner) EIP712("ContentShareRegistry", "1") {}

    /**
     * @dev 记录内容分享信息
     * @param dataHash 内容数据的哈希值
     * @param publisher 内容发布者钱包地址
     * @param shareId 业务系统的唯一 ID
     * @param metadata 内容元数据 (JSON string)，仅用于事件记录，不存 Storage
     * @param signature 发布者对上述数据的签名
     */
    function recordShare(
        bytes32 dataHash,
        address publisher,
        uint256 shareId,
        string calldata metadata,
        bytes calldata signature
    ) external onlyOwner {
        // 1. 基础校验
        require(dataHash != bytes32(0), "Invalid hash");
        require(publisher != address(0), "Invalid publisher");
        require(!shareIdUsed[shareId], "ShareId already used");
        
        ShareRecord storage record = records[dataHash];
        require(record.timestamp == 0, "Content hash already recorded");

        // 2. 构建结构化数据的哈希 (StructHash)
        bytes32 structHash = keccak256(
            abi.encode(
                SHARE_TYPEHASH,
                dataHash,
                publisher,
                shareId,
                keccak256(bytes(metadata)) // 对长字符串进行 Hash 处理
            )
        );

        // 3. 计算 EIP-712 digest
        bytes32 digest = _hashTypedDataV4(structHash);

        // 4. 恢复签名者并验证 (signTypedData)
        address signer = digest.recover(signature);
        require(signer == publisher, "Invalid signature: verify failed");

        // 5. 写入状态 (只存最核心的数据，省 Gas)
        records[dataHash] = ShareRecord({
            publisher: publisher,
            shareId: shareId,
            timestamp: block.timestamp
        });
        
        shareIdUsed[shareId] = true;

        // 6. 抛出事件 (完整数据在这里，供前端和索引器读取)
        emit ShareRecorded(dataHash, publisher, shareId, metadata, block.timestamp);
    }

    /**
     * @dev 查询确权记录
     */
    function getShare(bytes32 dataHash)
        external
        view
        returns (address publisher, uint256 shareId, uint256 timestamp)
    {
        ShareRecord memory record = records[dataHash];
        return (record.publisher, record.shareId, record.timestamp);
    }

    /**
     * @dev 验证某个 Hash 是否已确权
     */
    function verifyShare(bytes32 dataHash) external view returns (bool) {
        return records[dataHash].timestamp != 0;
    }

    /**
     * @dev 检查 ShareId 是否已使用
     */
    function isShareIdUsed(uint256 shareId) external view returns (bool) {
        return shareIdUsed[shareId];
    }
}
