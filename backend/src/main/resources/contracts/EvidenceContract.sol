pragma solidity ^0.4.25;
pragma experimental ABIEncoderV2;

/**
 * @title EvidenceContract
 * @dev 竞赛平台区块链存证智能合约
 * @author 竞赛平台
 * @notice 支持多种业务类型的存证：提交作品、榜单快照、奖金批次、评测结果、提现申请等
 */
contract EvidenceContract {

    // 存证结构体（优化为竞赛平台业务）
    struct Evidence {
        string dataHash;        // 数据哈希（文件哈希或Merkle Root）
        string bizType;         // 业务类型（SUBMISSION/LEADERBOARD/PRIZE_BATCH/EVALUATION/WITHDRAW）
        string bizId;           // 业务ID（提交ID/竞赛ID/批次号/评测ID/提现申请ID）
        string uploader;        // 上传者（用户ID或系统）
        string metadata;        // 元数据（JSON格式）
        uint256 timestamp;      // 时间戳
        bool exists;            // 是否存在
    }

    // 存证映射：数据哈希 => 存证信息
    mapping(string => Evidence) private evidences;

    // 业务类型存证列表：业务类型 => 数据哈希数组
    mapping(string => string[]) private bizTypeEvidences;

    // 用户存证列表：上传者 => 数据哈希数组
    mapping(string => string[]) private userEvidences;

    // 所有存证的数据哈希数组
    string[] private allHashes;

    // 合约所有者
    address public owner;

    // 事件定义
    event EvidenceAdded(
        string dataHash,
        string bizType,
        string bizId,
        string uploader,
        uint256 timestamp
    );

    event EvidenceVerified(
        string dataHash,
        address indexed verifier,
        bool result
    );

    event EvidenceUpdated(
        string dataHash,
        string metadata,
        uint256 timestamp
    );

    // 构造函数
    constructor() public {
        owner = msg.sender;
    }

    // 修饰符：仅合约所有者
    modifier onlyOwner() {
        require(msg.sender == owner, "Only owner can call this function");
        _;
    }

    // 修饰符：检查字符串不为空
    modifier notEmpty(string memory str) {
        require(bytes(str).length > 0, "String cannot be empty");
        _;
    }

    /**
     * @dev 添加存证
     * @param _dataHash 数据哈希（文件哈希或Merkle Root）
     * @param _bizType 业务类型（SUBMISSION/LEADERBOARD/PRIZE_BATCH/EVALUATION/WITHDRAW）
     * @param _bizId 业务ID（提交ID/竞赛ID/批次号/评测ID/提现申请ID）
     * @param _uploader 上传者（用户ID或系统标识）
     * @param _metadata 元数据（JSON格式，可选）
     * @return success 是否成功
     */
    function addEvidence(
        string memory _dataHash,
        string memory _bizType,
        string memory _bizId,
        string memory _uploader,
        string memory _metadata
    )
    public
    notEmpty(_dataHash)
    notEmpty(_bizType)
    notEmpty(_bizId)
    notEmpty(_uploader)
    returns (bool success)
    {
        // 检查存证是否已存在
        require(!evidences[_dataHash].exists, "Evidence already exists");

        // 创建存证记录
        evidences[_dataHash] = Evidence({
            dataHash: _dataHash,
            bizType: _bizType,
            bizId: _bizId,
            uploader: _uploader,
            metadata: _metadata,
            timestamp: block.timestamp,
            exists: true
        });

        // 添加到业务类型存证列表
        bizTypeEvidences[_bizType].push(_dataHash);

        // 添加到用户存证列表
        userEvidences[_uploader].push(_dataHash);

        // 添加到全局哈希列表
        allHashes.push(_dataHash);

        // 触发事件
        emit EvidenceAdded(_dataHash, _bizType, _bizId, _uploader, block.timestamp);

        return true;
    }

    /**
     * @dev 根据数据哈希获取存证信息
     */
    function getEvidenceByHash(string memory _dataHash)
    public
    view
    notEmpty(_dataHash)
    returns (
        string memory dataHash,
        string memory bizType,
        string memory bizId,
        string memory uploader,
        string memory metadata,
        uint256 timestamp
    )
    {
        require(evidences[_dataHash].exists, "Evidence does not exist");

        Evidence memory evidence = evidences[_dataHash];
        return (
            evidence.dataHash,
            evidence.bizType,
            evidence.bizId,
            evidence.uploader,
            evidence.metadata,
            evidence.timestamp
        );
    }

    /**
     * @dev 验证存证是否存在
     */
    function verifyEvidence(string memory _dataHash)
    public
    view
    notEmpty(_dataHash)
    returns (bool exists)
    {
        return evidences[_dataHash].exists;
    }

    /**
     * @dev 根据业务类型获取存证列表
     */
    function getEvidencesByBizType(string memory _bizType)
    public
    view
    notEmpty(_bizType)
    returns (string[] memory hashes)
    {
        return bizTypeEvidences[_bizType];
    }

    /**
     * @dev 获取用户的所有存证
     */
    function getEvidencesByUploader(string memory _uploader)
    public
    view
    notEmpty(_uploader)
    returns (string[] memory hashes)
    {
        return userEvidences[_uploader];
    }

    /**
     * @dev 获取存证总数
     */
    function getEvidenceCount()
    public
    view
    returns (uint256 count)
    {
        return allHashes.length;
    }

    /**
     * @dev 分页获取存证列表
     */
    function getEvidenceList(uint256 _offset, uint256 _limit)
    public
    view
    returns (string[] memory hashes)
    {
        require(_offset < allHashes.length, "Offset out of range");

        uint256 end = _offset + _limit;
        if (end > allHashes.length) {
            end = allHashes.length;
        }

        string[] memory result = new string[](end - _offset);
        for (uint256 i = _offset; i < end; i++) {
            result[i - _offset] = allHashes[i];
        }

        return result;
    }

    /**
     * @dev 批量验证存证
     */
    function batchVerifyEvidence(string[] memory _fileHashes)
    public
    view
    returns (bool[] memory results)
    {
        results = new bool[](_fileHashes.length);

        for (uint256 i = 0; i < _fileHashes.length; i++) {
            results[i] = evidences[_fileHashes[i]].exists;
        }

        return results;
    }

    /**
     * @dev 更新存证元数据
     */
    function updateEvidenceMetadata(
        string memory _dataHash,
        string memory _metadata
    )
    public
    notEmpty(_dataHash)
    returns (bool success)
    {
        require(evidences[_dataHash].exists, "Evidence does not exist");

        // 更新元数据
        evidences[_dataHash].metadata = _metadata;

        // 触发事件
        emit EvidenceUpdated(_dataHash, _metadata, block.timestamp);

        return true;
    }

    /**
     * @dev 获取合约基本信息
     */
    function getContractInfo()
    public
    view
    returns (
        address contractOwner,
        uint256 totalEvidences,
        uint256 contractBalance
    )
    {
        return (owner, allHashes.length, address(this).balance);
    }

    /**
     * @dev 紧急停止合约（仅所有者）
     */
    bool public stopped = false;

    modifier stopInEmergency() {
        require(!stopped, "Contract is stopped");
        _;
    }

    modifier onlyInEmergency() {
        require(stopped, "Contract is not stopped");
        _;
    }

    function emergencyStop() public onlyOwner {
        stopped = true;
    }

    function restart() public onlyOwner {
        stopped = false;
    }

    /**
     * @dev 转移合约所有权
     */
    function transferOwnership(address _newOwner) public onlyOwner {
        require(_newOwner != address(0), "New owner cannot be zero address");
        owner = _newOwner;
    }

    /**
     * @dev 销毁合约（慎用）
     */
    function destroy() public onlyOwner {
        selfdestruct(owner);
    }

    // 0.4.25 的回退函数写法（接收 ETH）
    function() external payable {}
}
