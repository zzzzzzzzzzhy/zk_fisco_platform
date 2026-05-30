// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/utils/cryptography/MerkleProof.sol";

/**
 * @title CoffeeWeeklyRewardDistributor
 * @dev 每周基于 Merkle 证明的咖啡算力奖励分发合约
 */
contract CoffeeWeeklyRewardDistributor is Ownable {
    IERC20 public immutable rewardToken;

    mapping(uint256 => bytes32) public merkleRoots;
    mapping(uint256 => mapping(address => bool)) public claimed;

    event MerkleRootUpdated(uint256 indexed epoch, bytes32 merkleRoot);
    event RewardClaimed(uint256 indexed epoch, address indexed account, uint256 amount);

    constructor(address initialOwner, address rewardToken_) Ownable(initialOwner) {
        require(rewardToken_ != address(0), "invalid token");
        rewardToken = IERC20(rewardToken_);
    }

    function setMerkleRoot(uint256 epoch, bytes32 merkleRoot) external onlyOwner {
        require(merkleRoot != bytes32(0), "invalid root");
        merkleRoots[epoch] = merkleRoot;
        emit MerkleRootUpdated(epoch, merkleRoot);
    }

    function claim(uint256 epoch, uint256 amount, bytes32[] calldata proof) external {
        bytes32 root = merkleRoots[epoch];
        require(root != bytes32(0), "root not set");
        require(!claimed[epoch][msg.sender], "already claimed");

        bytes32 leaf = keccak256(abi.encodePacked(msg.sender, amount, epoch));
        require(MerkleProof.verify(proof, root, leaf), "invalid proof");

        claimed[epoch][msg.sender] = true;
        require(rewardToken.transfer(msg.sender, amount), "transfer failed");
        emit RewardClaimed(epoch, msg.sender, amount);
    }

    function withdraw(address to, uint256 amount) external onlyOwner {
        require(to != address(0), "invalid recipient");
        require(rewardToken.transfer(to, amount), "transfer failed");
    }
}
