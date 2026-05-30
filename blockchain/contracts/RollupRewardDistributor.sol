// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

interface IContentRollupRegistry {
    function isBatchSubmitted(uint256 batchId) external view returns (bool);
}

/**
 * @title RollupRewardDistributor
 * @dev 后端统一发放奖励（需先有对应 rollup 批次上链）
 */
contract RollupRewardDistributor is Ownable {
    IERC20 public immutable rewardToken;
    IContentRollupRegistry public rollupRegistry;

    mapping(uint256 => bool) public batchDistributed;

    event BatchDistributed(uint256 indexed batchId, uint256 totalRecipients, uint256 totalAmount);

    constructor(address initialOwner, address tokenAddress, address registryAddress) Ownable(initialOwner) {
        require(tokenAddress != address(0), "invalid token");
        require(registryAddress != address(0), "invalid registry");
        rewardToken = IERC20(tokenAddress);
        rollupRegistry = IContentRollupRegistry(registryAddress);
    }

    function setRegistry(address registryAddress) external onlyOwner {
        require(registryAddress != address(0), "invalid registry");
        rollupRegistry = IContentRollupRegistry(registryAddress);
    }

    function distributeBatch(
        uint256 batchId,
        address[] calldata recipients,
        uint256[] calldata amounts
    ) external onlyOwner {
        require(rollupRegistry.isBatchSubmitted(batchId), "batch not submitted");
        require(!batchDistributed[batchId], "batch already distributed");
        require(recipients.length == amounts.length, "length mismatch");

        uint256 total = 0;
        for (uint256 i = 0; i < recipients.length; i++) {
            address recipient = recipients[i];
            uint256 amount = amounts[i];
            require(recipient != address(0), "invalid recipient");
            require(amount > 0, "invalid amount");
            total += amount;
            require(rewardToken.transfer(recipient, amount), "transfer failed");
        }

        batchDistributed[batchId] = true;
        emit BatchDistributed(batchId, recipients.length, total);
    }
}
