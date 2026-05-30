// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import "@openzeppelin/contracts/utils/cryptography/MessageHashUtils.sol";

/**
 * @title ForumTokenExtension
 * @dev 论坛代币扩展合约，基于现有的WEE代币
 */
contract ForumTokenExtension is Ownable, ReentrancyGuard {

    using ECDSA for bytes32;
    using MessageHashUtils for bytes32;

    // WEE代币合约地址 (在构造函数中注入)
    IERC20 public immutable WEE_TOKEN;
    ERC20Burnable public immutable WEE_BURNABLE;

    // 奖励配置
    struct RewardConfig {
        uint256 postReward;          // 发布帖子奖励
        uint256 commentReward;       // 评论奖励
        uint256 dailyCheckinReward;  // 每日签到奖励
        uint256 featuredPostReward;  // 精华帖子奖励
        uint256 consecutiveBonus;    // 连续签到奖励
        uint256 contentImageReward;  // 内容分享 - 图片
        uint256 contentVideoReward;  // 内容分享 - 视频
    }

    // 用户奖励记录
    struct UserReward {
        uint256 lastCheckinTime;     // 上次签到时间
        uint256 consecutiveDays;     // 连续签到天数
        uint256 dailyRewardAmount;   // 今日已获得奖励
        uint256 totalRewarded;       // 总奖励金额
        uint256 lastRewardDay;       // 上次重置每日奖励的天数（按UTC日）
    }

    // 奖励类型
    enum RewardType {
        POST,               // 发布帖子
        COMMENT,            // 发布评论
        DAILY_CHECKIN,      // 每日签到
        FEATURED_POST,      // 精华帖子
        CONSECUTIVE,        // 连续签到奖励
        CONTENT_IMAGE,      // 内容分享-图片
        CONTENT_VIDEO       // 内容分享-视频
    }

    // 配置变量
    RewardConfig public rewardConfig;
    // 治理合约地址（用于 DAO 参数治理）
    address public governance;

    // 离线签名领取奖励的签名者地址（用于防刷的授权签名）
    address public rewardSigner;
    uint256 public constant DAILY_REWARD_LIMIT = 100 * 10**18; // 每日奖励上限100 WEE
    uint256 public constant CONSECUTIVE_DAYS_FOR_BONUS = 7;     // 连续7天奖励

    // 状态变量
    mapping(address => UserReward) public userRewards;
    mapping(address => bool) public authorizedMinters;          // 授权铸造者
    mapping(string => bool) public usedPostIds;                 // 防重复发帖奖励
    mapping(string => bool) public usedCommentIds;              // 防重复评论奖励
    mapping(string => bool) public usedContentShareIds;         // 防重复内容分享奖励

    // 已使用的签名领取标识（避免同一签名重复使用），key 为结构化消息哈希
    mapping(bytes32 => bool) public usedRewardClaims;

    // 事件定义
    event RewardGranted(
        address indexed user,
        RewardType indexed rewardType,
        uint256 amount,
        string indexed contentId
    );

    event DailyCheckin(
        address indexed user,
        uint256 consecutiveDays,
        uint256 rewardAmount
    );

    event RewardConfigUpdated(RewardConfig newConfig);

    event GovernanceUpdated(address indexed oldGovernance, address indexed newGovernance);

    event RewardSignerUpdated(address indexed oldSigner, address indexed newSigner);

    modifier onlyAuthorizedMinter() {
        require(authorizedMinters[msg.sender] || msg.sender == owner(),
                "ForumTokenExtension: Not authorized to mint rewards");
        _;
    }

    /**
     * @dev 只有治理合约或所有者才能调用的修饰符
     * 便于后续将权限完全交由 DAO 治理，同时保留 Owner 作为兜底控制人（可选）
     */
    modifier onlyGovernance() {
        require(
            msg.sender == governance || msg.sender == owner(),
            "ForumTokenExtension: caller is not governance"
        );
        _;
    }

    /**
     * @dev 仅允许离线签名者地址（rewardSigner）使用的修饰符
     * 该地址用于发放「可领取奖励」的签名，本身不直接发链上交易
     */
    modifier onlyRewardSigner() {
        require(msg.sender == rewardSigner, "ForumTokenExtension: caller is not reward signer");
        _;
    }

    constructor(address initialOwner, address weeToken) Ownable(initialOwner) {
        require(weeToken != address(0), "ForumTokenExtension: invalid token");
        WEE_TOKEN = IERC20(weeToken);
        WEE_BURNABLE = ERC20Burnable(weeToken);
        // 初始化奖励配置 (WEE有18位小数)
        rewardConfig = RewardConfig({
            postReward: 10 * 10**18,         // 10 WEE
            commentReward: 2 * 10**18,       // 2 WEE
            dailyCheckinReward: 5 * 10**18,  // 5 WEE
            featuredPostReward: 50 * 10**18, // 50 WEE
            consecutiveBonus: 30 * 10**18,   // 30 WEE
            contentImageReward: 5 * 10**18,  // 5 WEE
            contentVideoReward: 10 * 10**18  // 10 WEE
        });

        // 初始情况下，治理地址默认为合约所有者，可在后续迁移到 DAO 合约
        governance = initialOwner;
        // 初始情况下，将 rewardSigner 也设置为所有者，可在后续单独迁移到专用签名地址/服务
        rewardSigner = initialOwner;
    }

    /**
     * @dev 兼容旧接口，返回当前代币地址
     */
    function MTK_TOKEN() external view returns (address) {
        return address(WEE_TOKEN);
    }

    /**
     * @dev 授权铸造者 (用于后端服务)
     */
    function authorizeMinter(address minter, bool authorized) external onlyOwner {
        authorizedMinters[minter] = authorized;
    }

    /**
     * @dev 更新奖励配置
     * v1：由 Owner 或治理合约调用；未来可将 Owner 权限移交给多签或完全交由 DAO
     */
    function updateRewardConfig(RewardConfig calldata newConfig) external onlyGovernance {
        rewardConfig = newConfig;
        emit RewardConfigUpdated(newConfig);
    }

    /**
     * @dev 设置治理合约地址（仅 Owner 可调用）
     * 后续可以将此地址指向 DAO Governor 合约，由其通过提案修改奖励配置等参数
     */
    function setGovernance(address newGovernance) external onlyOwner {
        require(newGovernance != address(0), "ForumTokenExtension: invalid governance");
        address old = governance;
        governance = newGovernance;
        emit GovernanceUpdated(old, newGovernance);
    }

    /**
     * @dev 设置离线签名者地址（仅 Owner 可调用）
     * 后续前后端将通过该地址生成的签名，让用户自行在链上领取对应奖励
     */
    function setRewardSigner(address newSigner) external onlyOwner {
        require(newSigner != address(0), "ForumTokenExtension: invalid reward signer");
        address old = rewardSigner;
        rewardSigner = newSigner;
        emit RewardSignerUpdated(old, newSigner);
    }

    // ------------------------------------------------------------
    // 签名领取奖励（防刷版接口）
    // ------------------------------------------------------------

    /**
     * @dev 构造统一的领取奖励消息哈希（避免不同业务碰撞）
     * 说明：
     * - 使用合约地址 + chainId + user + rewardType + bizId + deadline 作为唯一标识
     * - 由后端使用 rewardSigner 私钥对该哈希做以太坊标准签名（eth_sign）
     */
    function _hashRewardMessage(
        address user,
        RewardType rewardType,
        string memory bizId,
        uint256 deadline
    ) internal view returns (bytes32) {
        return keccak256(
            abi.encodePacked(
                "WEE_REWARD",
                address(this),
                block.chainid,
                user,
                rewardType,
                bizId,
                deadline
            )
        );
    }

    /**
     * @dev 校验签名并标记使用
     */
    function _useRewardSignature(
        address user,
        RewardType rewardType,
        string memory bizId,
        uint256 deadline,
        bytes calldata signature
    ) internal {
        require(block.timestamp <= deadline, "ForumTokenExtension: signature expired");
        bytes32 msgHash = _hashRewardMessage(user, rewardType, bizId, deadline);
        require(!usedRewardClaims[msgHash], "ForumTokenExtension: reward already claimed");

        // 以太坊标准消息前缀签名
        bytes32 ethHash = msgHash.toEthSignedMessageHash();
        address signer = ethHash.recover(signature);
        require(signer == rewardSigner, "ForumTokenExtension: invalid reward signature");

        usedRewardClaims[msgHash] = true;
    }

    /**
     * @dev 用户自行在链上领取「发帖奖励」，需携带后端签名
     * @param user 奖励接收地址（必须等于 msg.sender）
     * @param postId 帖子业务ID（后端/平台保证唯一）
     * @param deadline 签名有效期
     * @param signature 后端使用 rewardSigner 私钥生成的签名
     */
    function claimPostReward(
        address user,
        string calldata postId,
        uint256 deadline,
        bytes calldata signature
    ) external nonReentrant {
        require(user == msg.sender, "ForumTokenExtension: can only claim for self");
        require(user != address(0), "ForumTokenExtension: invalid user");
        require(!usedPostIds[postId], "ForumTokenExtension: post already rewarded");

        _useRewardSignature(user, RewardType.POST, postId, deadline, signature);

        uint256 rewardAmount = rewardConfig.postReward;
        require(rewardAmount > 0, "ForumTokenExtension: invalid reward config");

        UserReward storage reward = userRewards[user];
        _syncDailyReward(reward);

        // 检查每日奖励上限
        require(_checkDailyLimit(reward, rewardAmount), "ForumTokenExtension: daily limit exceeded");

        _grantReward(user, RewardType.POST, rewardAmount, postId);
        _increaseDailyReward(reward, rewardAmount);
        usedPostIds[postId] = true;
    }

    /**
     * @dev 用户自行在链上领取「评论奖励」
     */
    function claimCommentReward(
        address user,
        string calldata commentId,
        uint256 deadline,
        bytes calldata signature
    ) external nonReentrant {
        require(user == msg.sender, "ForumTokenExtension: can only claim for self");
        require(user != address(0), "ForumTokenExtension: invalid user");
        require(!usedCommentIds[commentId], "ForumTokenExtension: comment already rewarded");

        _useRewardSignature(user, RewardType.COMMENT, commentId, deadline, signature);

        uint256 rewardAmount = rewardConfig.commentReward;
        require(rewardAmount > 0, "ForumTokenExtension: invalid reward config");

        UserReward storage reward = userRewards[user];
        _syncDailyReward(reward);

        require(_checkDailyLimit(reward, rewardAmount), "ForumTokenExtension: daily limit exceeded");

        _grantReward(user, RewardType.COMMENT, rewardAmount, commentId);
        _increaseDailyReward(reward, rewardAmount);
        usedCommentIds[commentId] = true;
    }

    /**
     * @dev 用户自行在链上领取「内容分享奖励」（图片/视频）
     */
    function claimContentShareReward(
        address user,
        string calldata contentId,
        bool isVideo,
        uint256 deadline,
        bytes calldata signature
    ) external nonReentrant {
        require(user == msg.sender, "ForumTokenExtension: can only claim for self");
        require(user != address(0), "ForumTokenExtension: invalid user");
        require(!usedContentShareIds[contentId], "ForumTokenExtension: content already rewarded");

        _useRewardSignature(
            user,
            isVideo ? RewardType.CONTENT_VIDEO : RewardType.CONTENT_IMAGE,
            contentId,
            deadline,
            signature
        );

        uint256 rewardAmount = isVideo ? rewardConfig.contentVideoReward : rewardConfig.contentImageReward;
        require(rewardAmount > 0, "ForumTokenExtension: invalid reward config");

        UserReward storage reward = userRewards[user];
        _syncDailyReward(reward);

        require(_checkDailyLimit(reward, rewardAmount), "ForumTokenExtension: daily limit exceeded");

        _grantReward(
            user,
            isVideo ? RewardType.CONTENT_VIDEO : RewardType.CONTENT_IMAGE,
            rewardAmount,
            contentId
        );
        _increaseDailyReward(reward, rewardAmount);
        usedContentShareIds[contentId] = true;
    }

    /**
     * @dev 用户自行在链上领取「每日签到奖励」
     * day 参数为 UTC 日（timestamp / 86400），后端用它来构造 bizId 及签名
     */
    function claimDailyCheckinReward(
        address user,
        uint256 day,
        uint256 deadline,
        bytes calldata signature
    ) external nonReentrant {
        require(user == msg.sender, "ForumTokenExtension: can only claim for self");
        require(user != address(0), "ForumTokenExtension: invalid user");

        // 只允许当前日的签到，避免提前/回溯领取
        uint256 currentDay = block.timestamp / 86400;
        require(day == currentDay, "ForumTokenExtension: invalid day");

        // 使用 day 作为 bizId 的一部分，确保每天只能签到一次（配合链上状态再次校验）
        string memory bizId = string(abi.encodePacked("checkin_", _toString(day)));
        _useRewardSignature(user, RewardType.DAILY_CHECKIN, bizId, deadline, signature);

        // 复用原有的签到逻辑，确保连续签到 / 日上限逻辑不变
        UserReward storage userReward = userRewards[user];
        _syncDailyReward(userReward);
        uint256 currentTime = block.timestamp;
        uint256 rewardAmount = rewardConfig.dailyCheckinReward;

        uint256 lastCheckinDay = userReward.lastCheckinTime / 86400;

        require(currentDay > lastCheckinDay, "ForumTokenExtension: already checked in today");

        if (currentDay == lastCheckinDay + 1) {
            userReward.consecutiveDays++;
            if (userReward.consecutiveDays % CONSECUTIVE_DAYS_FOR_BONUS == 0) {
                rewardAmount += rewardConfig.consecutiveBonus;
            }
        } else {
            userReward.consecutiveDays = 1;
        }

        require(
            userReward.dailyRewardAmount + rewardAmount <= DAILY_REWARD_LIMIT,
            "ForumTokenExtension: daily reward limit exceeded"
        );

        _grantReward(user, RewardType.DAILY_CHECKIN, rewardAmount, "daily_checkin");

        userReward.lastCheckinTime = currentTime;
        userReward.dailyRewardAmount += rewardAmount;

        emit DailyCheckin(user, userReward.consecutiveDays, rewardAmount);
    }

    /**
     * @dev 内部工具：uint256 转字符串（简化版，仅用于构造 bizId）
     */
    function _toString(uint256 value) internal pure returns (string memory) {
        if (value == 0) {
            return "0";
        }
        uint256 temp = value;
        uint256 digits;
        while (temp != 0) {
            digits++;
            temp /= 10;
        }
        bytes memory buffer = new bytes(digits);
        while (value != 0) {
            digits -= 1;
            buffer[digits] = bytes1(uint8(48 + uint256(value % 10)));
            value /= 10;
        }
        return string(buffer);
    }

    /**
     * @dev [已废弃] 旧版每日签到入口（任何人可直接调用）
     *
     * 安全考虑：为防止女巫攻击与绕过后端风控，本函数已彻底废弃，直接 revert。
     * 请前端/后端统一迁移到基于签名的 claimDailyCheckinReward 接口。
     */
    function checkin() external pure {
        revert("ForumTokenExtension: checkin deprecated, use claimDailyCheckinReward");
    }

    /**
     * @dev 每日签到 (管理员调用，用于批量处理)
     */
    function dailyCheckin(address /*user*/) external pure {
        revert("ForumTokenExtension: dailyCheckin deprecated, use claimDailyCheckinReward");
    }

    /**
     * @dev 发布帖子奖励
     */
    function rewardPost(address /*user*/, string calldata /*postId*/) external pure {
        revert("ForumTokenExtension: rewardPost deprecated, use claimPostReward");
    }

    /**
     * @dev 发布评论奖励
     */
    function rewardComment(address /*user*/, string calldata /*commentId*/) external pure {
        revert("ForumTokenExtension: rewardComment deprecated, use claimCommentReward");
    }

    /**
     * @dev 内容分享奖励（图片/视频）
     */
    function rewardContentShare(
        address /*user*/,
        string calldata /*contentId*/,
        bool /*isVideo*/
    ) external pure {
        revert("ForumTokenExtension: rewardContentShare deprecated, use claimContentShareReward");
    }

    /**
     * @dev 精华帖子奖励
     */
    function rewardFeaturedPost(address /*user*/, string calldata /*postId*/) external pure {
        revert("ForumTokenExtension: rewardFeaturedPost deprecated");
    }

    /**
     * @dev 批量奖励帖子/评论
     */
    function batchRewardPosts(
        address[] calldata /*users*/,
        string[] calldata /*contentIds*/,
        RewardType /*rewardType*/
    ) external pure {
        revert("ForumTokenExtension: batchRewardPosts deprecated, use per-user claim functions");
    }

    /**
     * @dev 检查用户代币余额
     */
    function getUserTokenBalance(address user) external view returns (uint256) {
        return WEE_TOKEN.balanceOf(user);
    }

    /**
     * @dev 获取用户奖励信息
     */
    function getUserRewardInfo(address user) external view returns (
        uint256 lastCheckinTime,
        uint256 consecutiveDays,
        uint256 dailyRewardAmount,
        uint256 totalRewarded
    ) {
        UserReward storage userReward = userRewards[user];
        return (
            userReward.lastCheckinTime,
            userReward.consecutiveDays,
            userReward.dailyRewardAmount,
            userReward.totalRewarded
        );
    }

    /**
     * @dev 检查今日是否可以签到
     */
    function canCheckinToday(address user) external view returns (bool) {
        UserReward storage userReward = userRewards[user];
        uint256 lastCheckinDay = userReward.lastCheckinTime / 86400;
        uint256 currentDay = block.timestamp / 86400;
        return currentDay > lastCheckinDay;
    }

    /**
     * @dev 重置每日奖励 (定时任务调用)
     */
    function resetDailyRewards() external onlyOwner {
        // 这个函数可以由后端定时任务每天调用
        // 由于gas费用考虑，建议使用批量处理
    }

    /**
     * @dev 紧急提取代币 (仅所有者)
     */
    function emergencyWithdraw(uint256 amount) external onlyOwner {
        require(WEE_TOKEN.transfer(owner(), amount), "ForumTokenExtension: Transfer failed");
    }

    // 打赏和付费功能

    /**
     * @dev 打赏内容创作者
     */
    function tipContent(
        address creator,
        uint256 amount,
        string calldata contentType, // "post" or "content_share"
        string calldata contentId
    ) external nonReentrant {
        require(creator != address(0), "ForumTokenExtension: Invalid creator address");
        require(creator != msg.sender, "ForumTokenExtension: Cannot tip yourself");
        require(amount > 0, "ForumTokenExtension: Tip amount must be greater than 0");

        // 检查用户余额
        require(WEE_TOKEN.balanceOf(msg.sender) >= amount, "ForumTokenExtension: Insufficient balance");

        // 直接从打赏者转给创作者（需要事先 approve 本合约）
        bool sentToCreator = WEE_TOKEN.transferFrom(msg.sender, creator, amount);
        require(sentToCreator, "ForumTokenExtension: Transfer to creator failed (check allowance)");

        // 记录打赏事件
        emit ContentTipped(msg.sender, creator, amount, contentType, contentId);
    }

    /**
     * @dev 付费置顶帖子（用户自行调用，前端钱包支付 Gas 和置顶费用）
     *
     * 流程：
     * 1. 用户在前端点击置顶，前端通过 WEE_TOKEN.approve 授权本合约消费足够额度
     * 2. 前端调用本函数，msg.sender 即为 user 地址
     * 3. 合约从用户地址 transferFrom 指定 amount 到合约地址，并触发 PostPinned 事件
     *
     * 注意：
     * - amount 建议固定为 _getPinPrice()（例如 50 WEE），也可以传入更高数值做扩展
     * - 后端可根据 PostPinned 事件及 txHash 做记录和风控
     */
    function purchasePinPost(
        address user,
        uint256 amount,
        string calldata postId
    ) external nonReentrant {
        require(user != address(0), "ForumTokenExtension: Invalid user address");
        require(msg.sender == user, "ForumTokenExtension: can only pin for self");
        require(amount >= _getPinPrice(), "ForumTokenExtension: Insufficient pin payment");

        // 检查用户余额
        require(WEE_TOKEN.balanceOf(user) >= amount, "ForumTokenExtension: Insufficient balance");

        // 从用户地址转入置顶费用（需要事先 approve 本合约）
        bool sentToContract = WEE_TOKEN.transferFrom(user, address(this), amount);
        require(
            sentToContract,
            "ForumTokenExtension: Transfer to contract failed. User may not have approved."
        );

        // 记录置顶事件
        emit PostPinned(user, postId, amount);
    }

    /**
     * @dev 获取置顶价格（可以动态调整）
     */
    function _getPinPrice() internal pure returns (uint256) {
        return 50 * 10**18; // 50 WEE
    }

    /**
     * @dev 燃烧代币（减少供应量）
     */
    function burnTokens(uint256 amount) external nonReentrant {
        require(amount > 0, "ForumTokenExtension: Burn amount must be greater than 0");
        require(WEE_TOKEN.balanceOf(msg.sender) >= amount, "ForumTokenExtension: Insufficient balance");

        // 先把代币转入本合约，再立即调用代币合约的 burn，确保总供应量真实减少
        bool sentToContract = WEE_TOKEN.transferFrom(msg.sender, address(this), amount);
        require(sentToContract, "ForumTokenExtension: Transfer to contract failed. User may not have approved.");

        // 使用代币合约自身的销毁逻辑（需 WEE 支持 ERC20Burnable）
        WEE_BURNABLE.burn(amount);

        emit TokensBurned(msg.sender, amount);
    }

    // 事件定义（新增）
    event ContentTipped(
        address indexed tipper,
        address indexed creator,
        uint256 amount,
        string contentType,
        string contentId
    );

    event PostPinned(
        address indexed user,
        string postId,
        uint256 amount
    );

    event TokensBurned(
        address indexed user,
        uint256 amount
    );

    // 内部函数

    /**
     * @dev 发放奖励的内部函数
     */
    function _grantReward(
        address user,
        RewardType rewardType,
        uint256 amount,
        string memory contentId
    ) internal {
        require(WEE_TOKEN.balanceOf(address(this)) >= amount,
                "ForumTokenExtension: Insufficient token balance");

        require(WEE_TOKEN.transfer(user, amount), "ForumTokenExtension: Transfer failed");

        userRewards[user].totalRewarded += amount;

        emit RewardGranted(user, rewardType, amount, contentId);
    }

    /**
     * @dev 检查每日奖励限制
     */
    function _checkDailyLimit(UserReward storage reward, uint256 additionalAmount) internal view returns (bool) {
        return (reward.dailyRewardAmount + additionalAmount) <= DAILY_REWARD_LIMIT;
    }

    /**
     * @dev 发放奖励后，记录当日累计
     */
    function _increaseDailyReward(UserReward storage reward, uint256 amount) internal {
        reward.dailyRewardAmount += amount;
        reward.lastRewardDay = block.timestamp / 86400;
    }

    /**
     * @dev 如果跨天则重置每日奖励计数
     */
    function _syncDailyReward(UserReward storage reward) internal {
        uint256 currentDay = block.timestamp / 86400;
        if (currentDay > reward.lastRewardDay) {
            reward.dailyRewardAmount = 0;
            reward.lastRewardDay = currentDay;
        }
    }
}
