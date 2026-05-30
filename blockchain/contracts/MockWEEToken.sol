// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title MockWEEToken
 * @dev 本地测试用的 WEE 代币，支持任意 mint（仅用于开发/测试）
 */
contract MockWEEToken is ERC20Burnable, Ownable {
    constructor() ERC20("WEE Ecosystem Token", "WEE") Ownable(msg.sender) {
        // 给部署者 mint 1,000,000 WEE
        _mint(msg.sender, 1_000_000 * 10 ** 18);
    }

    function mint(address to, uint256 amount) external onlyOwner {
        _mint(to, amount);
    }
}
