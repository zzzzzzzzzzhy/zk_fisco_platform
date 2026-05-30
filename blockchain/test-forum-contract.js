const { ethers } = require("hardhat");

async function main() {
  console.log("🧪 测试新部署的论坛合约功能...");

  // 新的论坛合约地址
  const forumContractAddress = "0xFd6396cA4aAcd6081EC15EA88D1bFFfde7334634";

  // 新的 MTK 代币地址
  const mtkTokenAddress = "0x3b90669eB9960d1e65D3A09097a9363Df74783DD";

  // 合约 ABI
  const forumABI = [
    "function checkin() external",
    "function canCheckinToday(address user) external view returns (bool)",
    "function getUserRewardInfo(address user) external view returns (uint256, uint256, uint256, uint256)",
    "function getUserTokenBalance(address user) external view returns (uint256)",
    "function rewardConfig() external view returns (uint256, uint256, uint256, uint256, uint256, uint256, uint256)",
    "function MTK_TOKEN() external view returns (address)"
  ];

  const mtkABI = [
    "function balanceOf(address account) external view returns (uint256)",
    "function transfer(address to, uint256 amount) external returns (bool)",
    "function decimals() external view returns (uint8)"
  ];

  // 检查网络
  const network = await ethers.provider.getNetwork();
  console.log("当前网络:", network.name);
  console.log("链ID:", Number(network.chainId));

  if (Number(network.chainId) !== 80002) {
    throw new Error("请确保连接到 Polygon Amoy 测试网 (ChainID: 80002)");
  }

  // 获取签名者
  const [deployer] = await ethers.getSigners();
  console.log("测试账户地址:", deployer.address);

  const balance = await ethers.provider.getBalance(deployer.address);
  console.log("账户 MATIC 余额:", ethers.formatEther(balance));

  // 连接合约
  const forumContract = new ethers.Contract(forumContractAddress, forumABI, deployer);
  const mtkContract = new ethers.Contract(mtkTokenAddress, mtkABI, deployer);

  try {
    console.log("\n📋 验证合约基本信息...");

    // 检查 MTK 代币地址
    const contractMtkAddress = await forumContract.MTK_TOKEN();
    console.log("✅ 论坛合约中的 MTK 地址:", contractMtkAddress);
    console.log("✅ 预期的 MTK 地址:", mtkTokenAddress);

    if (contractMtkAddress.toLowerCase() === mtkTokenAddress.toLowerCase()) {
      console.log("✅ MTK 代币地址匹配!");
    } else {
      throw new Error("❌ MTK 代币地址不匹配!");
    }

    // 检查奖励配置
    const rewardConfig = await forumContract.rewardConfig();
    console.log("\n🎁 奖励配置:");
    console.log("- 发布帖子奖励:", ethers.formatEther(rewardConfig[0]), "MTK");
    console.log("- 评论奖励:", ethers.formatEther(rewardConfig[1]), "MTK");
    console.log("- 每日签到奖励:", ethers.formatEther(rewardConfig[2]), "MTK");
    console.log("- 精华帖子奖励:", ethers.formatEther(rewardConfig[3]), "MTK");
    console.log("- 连续签到奖励:", ethers.formatEther(rewardConfig[4]), "MTK");
    console.log("- 内容分享(图片)奖励:", ethers.formatEther(rewardConfig[5]), "MTK");
    console.log("- 内容分享(视频)奖励:", ethers.formatEther(rewardConfig[6]), "MTK");

    console.log("\n💰 检查用户代币余额...");
    const mtkBalance = await forumContract.getUserTokenBalance(deployer.address);
    console.log("✅ MTK 代币余额:", ethers.formatEther(mtkBalance));

    console.log("\n📅 检查签到状态...");
    const canCheckin = await forumContract.canCheckinToday(deployer.address);
    console.log("✅ 今日是否可以签到:", canCheckin);

    // 获取用户奖励信息
    const rewardInfo = await forumContract.getUserRewardInfo(deployer.address);
    console.log("\n🏆 用户奖励信息:");
    console.log("- 上次签到时间:", rewardInfo[0].toString() === '0' ? '从未签到' : new Date(Number(rewardInfo[0]) * 1000).toLocaleString());
    console.log("- 连续签到天数:", rewardInfo[1].toString());
    console.log("- 今日已获得奖励:", ethers.formatEther(rewardInfo[2]), "MTK");
    console.log("- 总奖励金额:", ethers.formatEther(rewardInfo[3]), "MTK");

    if (canCheckin) {
      console.log("\n🎯 测试签到功能...");
      try {
        const tx = await forumContract.checkin();
        console.log("✅ 签到交易已提交:", tx.hash);

        console.log("等待交易确认...");
        const receipt = await tx.wait();
        console.log("✅ 签到成功! Gas 使用:", receipt.gasUsed.toString());

        // 获取新的奖励信息
        const newRewardInfo = await forumContract.getUserRewardInfo(deployer.address);
        console.log("\n🏆 签到后奖励信息:");
        console.log("- 上次签到时间:", new Date(Number(newRewardInfo[0]) * 1000).toLocaleString());
        console.log("- 连续签到天数:", newRewardInfo[1].toString());
        console.log("- 今日已获得奖励:", ethers.formatEther(newRewardInfo[2]), "MTK");
        console.log("- 总奖励金额:", ethers.formatEther(newRewardInfo[3]), "MTK");

        // 检查新余额
        const newMtkBalance = await forumContract.getUserTokenBalance(deployer.address);
        console.log("✅ 签到后 MTK 余额:", ethers.formatEther(newMtkBalance));

      } catch (error) {
        console.error("❌ 签到失败:", error.message);
        if (error.reason) {
          console.error("失败原因:", error.reason);
        }
      }
    } else {
      console.log("⚠️  今日已经签到，无法重复签到");
    }

    console.log("\n🎉 论坛合约测试完成!");
    console.log("📊 合约状态总结:");
    console.log("- 论坛合约地址:", forumContractAddress);
    console.log("- MTK 代币地址:", contractMtkAddress);
    console.log("- 你的 MTK 余额:", ethers.formatEther(await forumContract.getUserTokenBalance(deployer.address)));
    console.log("- 连续签到天数:", (await forumContract.getUserRewardInfo(deployer.address))[1].toString());

    console.log("\n🔗 Polygon Amoy 浏览器链接:");
    console.log(`论坛合约: https://amoy.polygonscan.com/address/${forumContractAddress}`);
    console.log(`MTK 代币: https://amoy.polygonscan.com/address/${mtkTokenAddress}`);

  } catch (error) {
    console.error("❌ 测试失败:", error.message);
    if (error.reason) {
      console.error("失败原因:", error.reason);
    }
    throw error;
  }
}

main()
  .then(() => {
    console.log("\n✅ 新论坛合约测试成功! 签到功能正常工作");
    process.exit(0);
  })
  .catch((error) => {
    console.error("❌ 测试失败:", error);
    process.exit(1);
  });
