// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title RewardGovernor
 * @dev 一人一票治理合约，用于通过提案修改 ForumTokenExtension 的奖励参数。
 *      不依赖代币权重，每个已注册投票人拥有一票。
 *      Owner 可添加/移除投票人，提案通过后可调用 ForumTokenExtension.updateRewardConfig。
 */
contract RewardGovernor is Ownable {

    // -----------------------------------------------------------------------
    // 类型定义
    // -----------------------------------------------------------------------

    /**
     * @dev 提案状态枚举
     *  0 Pending   - 刚创建，等待投票期开始（当前实现中提案创建即进入 Active）
     *  1 Active    - 投票进行中
     *  2 Succeeded - 投票期结束，赞成票 >= QUORUM 且 赞成票 > 反对票
     *  3 Defeated  - 投票期结束，未达法定人数或反对票 >= 赞成票
     *  4 Executed  - 已执行
     */
    enum ProposalStatus {
        Pending,
        Active,
        Succeeded,
        Defeated,
        Executed
    }

    struct Proposal {
        uint256 id;
        address proposer;
        string description;
        bytes callData;          // 编码好的 updateRewardConfig(RewardConfig) calldata
        uint256 forVotes;
        uint256 againstVotes;
        ProposalStatus status;
        uint256 createdAt;
        uint256 votingDeadline;
    }

    // -----------------------------------------------------------------------
    // 状态变量
    // -----------------------------------------------------------------------

    /// @notice 目标合约：ForumTokenExtension
    address public immutable forumTokenExtension;

    /// @notice 投票周期（秒），默认 2 天，可在构造时覆盖
    uint256 public immutable VOTING_PERIOD;

    /// @notice 法定人数：最少需要多少赞成票提案才能通过
    uint256 public constant QUORUM = 1;

    /// @notice 提案计数器，从 1 开始
    uint256 private _proposalCount;

    /// @notice 提案存储
    mapping(uint256 => Proposal) private _proposals;

    /// @notice 已注册的投票人
    mapping(address => bool) public isVoter;

    /// @notice 投票记录：proposalId => voter => hasVoted
    mapping(uint256 => mapping(address => bool)) public hasVoted;

    // -----------------------------------------------------------------------
    // 事件
    // -----------------------------------------------------------------------

    event VoterAdded(address indexed voter);
    event VoterRemoved(address indexed voter);

    event ProposalCreated(
        uint256 indexed proposalId,
        address indexed proposer,
        string description,
        uint256 votingDeadline
    );

    event VoteCast(
        uint256 indexed proposalId,
        address indexed voter,
        bool support,
        uint256 forVotes,
        uint256 againstVotes
    );

    event ProposalExecuted(uint256 indexed proposalId);

    // -----------------------------------------------------------------------
    // 构造函数
    // -----------------------------------------------------------------------

    /**
     * @param initialOwner      合约所有者地址
     * @param _forumTokenExt    ForumTokenExtension 合约地址
     * @param votingPeriod      投票周期（秒）；传 0 时使用默认值 2 天
     */
    constructor(
        address initialOwner,
        address _forumTokenExt,
        uint256 votingPeriod
    ) Ownable(initialOwner) {
        require(_forumTokenExt != address(0), "RewardGovernor: invalid forumTokenExtension");
        forumTokenExtension = _forumTokenExt;
        VOTING_PERIOD = votingPeriod == 0 ? 2 days : votingPeriod;
    }

    // -----------------------------------------------------------------------
    // 投票人管理（仅 Owner）
    // -----------------------------------------------------------------------

    /**
     * @notice 添加投票人
     * @param voter 要添加的地址
     */
    function addVoter(address voter) external onlyOwner {
        require(voter != address(0), "RewardGovernor: invalid voter address");
        require(!isVoter[voter], "RewardGovernor: already a voter");
        isVoter[voter] = true;
        emit VoterAdded(voter);
    }

    /**
     * @notice 移除投票人
     * @param voter 要移除的地址
     */
    function removeVoter(address voter) external onlyOwner {
        require(isVoter[voter], "RewardGovernor: not a voter");
        isVoter[voter] = false;
        emit VoterRemoved(voter);
    }

    // -----------------------------------------------------------------------
    // 治理核心功能
    // -----------------------------------------------------------------------

    /**
     * @notice 创建提案
     * @dev 提案创建后立即进入 Active 状态，任何已注册投票人均可在截止时间前投票。
     *      callData 应为对 forumTokenExtension.updateRewardConfig(RewardConfig) 的完整
     *      ABI 编码调用数据，例如：
     *        abi.encodeWithSignature(
     *            "updateRewardConfig((uint256,uint256,uint256,uint256,uint256,uint256,uint256))",
     *            newConfig
     *        )
     * @param description  提案描述
     * @param _callData    调用 forumTokenExtension 的完整 calldata
     * @return proposalId  新提案的 ID
     */
    function propose(
        string calldata description,
        bytes calldata _callData
    ) external returns (uint256 proposalId) {
        require(isVoter[msg.sender] || msg.sender == owner(), "RewardGovernor: not authorized to propose");
        require(bytes(description).length > 0, "RewardGovernor: empty description");
        require(_callData.length > 0, "RewardGovernor: empty calldata");

        _proposalCount++;
        proposalId = _proposalCount;

        uint256 deadline = block.timestamp + VOTING_PERIOD;

        _proposals[proposalId] = Proposal({
            id: proposalId,
            proposer: msg.sender,
            description: description,
            callData: _callData,
            forVotes: 0,
            againstVotes: 0,
            status: ProposalStatus.Active,
            createdAt: block.timestamp,
            votingDeadline: deadline
        });

        emit ProposalCreated(proposalId, msg.sender, description, deadline);
    }

    /**
     * @notice 对提案投票（一人一票）
     * @param proposalId 提案 ID
     * @param support    true = 赞成，false = 反对
     */
    function vote(uint256 proposalId, bool support) external {
        require(isVoter[msg.sender], "RewardGovernor: not a registered voter");

        Proposal storage proposal = _proposals[proposalId];
        require(proposal.id != 0, "RewardGovernor: proposal does not exist");
        require(proposal.status == ProposalStatus.Active, "RewardGovernor: proposal not active");
        require(block.timestamp <= proposal.votingDeadline, "RewardGovernor: voting period ended");
        require(!hasVoted[proposalId][msg.sender], "RewardGovernor: already voted");

        hasVoted[proposalId][msg.sender] = true;

        if (support) {
            proposal.forVotes++;
        } else {
            proposal.againstVotes++;
        }

        emit VoteCast(proposalId, msg.sender, support, proposal.forVotes, proposal.againstVotes);
    }

    /**
     * @notice 执行已通过的提案
     * @dev 先检查提案是否处于 Succeeded 状态（若仍在 Active 则先触发状态结算）。
     *      执行时通过低级 call 将 callData 发送给 forumTokenExtension。
     * @param proposalId 提案 ID
     */
    function execute(uint256 proposalId) external {
        Proposal storage proposal = _proposals[proposalId];
        require(proposal.id != 0, "RewardGovernor: proposal does not exist");

        // 若投票期已结束但状态还是 Active，先结算最终状态
        if (proposal.status == ProposalStatus.Active && block.timestamp > proposal.votingDeadline) {
            _finalizeProposal(proposal);
        }

        require(proposal.status == ProposalStatus.Succeeded, "RewardGovernor: proposal not succeeded");

        // 标记为已执行，防止重入/重复执行
        proposal.status = ProposalStatus.Executed;

        (bool success, bytes memory returnData) = forumTokenExtension.call(proposal.callData);
        if (!success) {
            // 将底层错误冒泡出去，便于调试
            if (returnData.length > 0) {
                assembly {
                    revert(add(returnData, 32), mload(returnData))
                }
            }
            revert("RewardGovernor: execution failed");
        }

        emit ProposalExecuted(proposalId);
    }

    /**
     * @notice 查询提案详情
     * @param id 提案 ID
     * @return 提案结构体
     */
    function getProposal(uint256 id) external view returns (Proposal memory) {
        require(_proposals[id].id != 0, "RewardGovernor: proposal does not exist");
        return _proposals[id];
    }

    /**
     * @notice 查询提案当前状态（含动态结算）
     * @dev 返回 uint8，对应 ProposalStatus 枚举值：
     *      0 Pending / 1 Active / 2 Succeeded / 3 Defeated / 4 Executed
     * @param id 提案 ID
     * @return 状态值（0-4）
     */
    function state(uint256 id) external view returns (uint8) {
        Proposal storage proposal = _proposals[id];
        require(proposal.id != 0, "RewardGovernor: proposal does not exist");

        // 已执行 / 已最终化（非 Active）则直接返回
        if (proposal.status != ProposalStatus.Active) {
            return uint8(proposal.status);
        }

        // 投票期未结束 → Active
        if (block.timestamp <= proposal.votingDeadline) {
            return uint8(ProposalStatus.Active);
        }

        // 投票期已结束，动态计算最终状态（不修改存储，view 函数）
        if (proposal.forVotes >= QUORUM && proposal.forVotes > proposal.againstVotes) {
            return uint8(ProposalStatus.Succeeded);
        } else {
            return uint8(ProposalStatus.Defeated);
        }
    }

    /**
     * @notice 手动结算提案状态（投票期结束后可调用）
     * @dev 任何人均可调用，将 Active 提案的存储状态更新为 Succeeded / Defeated。
     * @param proposalId 提案 ID
     */
    function finalizeProposal(uint256 proposalId) external {
        Proposal storage proposal = _proposals[proposalId];
        require(proposal.id != 0, "RewardGovernor: proposal does not exist");
        require(proposal.status == ProposalStatus.Active, "RewardGovernor: proposal already finalized");
        require(block.timestamp > proposal.votingDeadline, "RewardGovernor: voting period not ended");
        _finalizeProposal(proposal);
    }

    /**
     * @notice 查询当前提案总数
     */
    function proposalCount() external view returns (uint256) {
        return _proposalCount;
    }

    // -----------------------------------------------------------------------
    // 内部函数
    // -----------------------------------------------------------------------

    /**
     * @dev 根据投票结果更新提案最终状态（仅在投票期结束后调用）
     */
    function _finalizeProposal(Proposal storage proposal) internal {
        if (proposal.forVotes >= QUORUM && proposal.forVotes > proposal.againstVotes) {
            proposal.status = ProposalStatus.Succeeded;
        } else {
            proposal.status = ProposalStatus.Defeated;
        }
    }
}
