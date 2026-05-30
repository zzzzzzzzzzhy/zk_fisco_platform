# Polygon 内容存证 - 前端集成指南

## 📝 变更说明

**原方案**：后端使用 Admin 账户主动调用 Polygon 合约存证，用户无需付 Gas。

**新方案**：用户在前端使用 MetaMask 签名并自己调用合约，自己支付 Gas费，后端验证交易并发放 WEE 奖励。

---

## 🎯 架构优势

1. ✅ **符合 Web3 去中心化原则** - 用户自己签名和发送交易
2. ✅ **防止恶意攻击** - 用户需要支付 Gas，防止刷内容
3. ✅ **降低后端成本** - 后端不再需要为每个内容支付 Gas
4. ✅ **激励机制** - 后端验证交易成功后，发放 WEE 代币奖励用户

---

## 📋 前端流程

### 1. 用户上传内容

```javascript
// 1.1 上传文件到 MinIO (现有逻辑不变)
const uploadResponse = await axios.post('/api/content-shares', {
  userId: currentUser.id,
  title: '我的内容',
  mediaType: 'IMAGE',
  mediaUrl: 'http://...',
  fileHash: 'abc123...',
  // ... 其他字段
});

const shareId = uploadResponse.data.id;
```

### 2. 获取 EIP-712 签名数据

```javascript
// 2.1 调用后端接口获取签名数据
const signDataResponse = await axios.get(`/api/content-shares/${shareId}/polygon-sign-data`);
const { domain, types, primaryType, message, contractAddress } = signDataResponse.data;

// EIP-712 数据结构示例：
// {
//   "domain": {
//     "name": "ContentShareRegistry",
//     "version": "1",
//     "chainId": 137,
//     "verifyingContract": "0x477c1FC569eCefE56a4e3D54616CC83AFB7d02E3"
//   },
//   "types": {
//     "EIP712Domain": [...],
//     "ContentShare": [...]
//   },
//   "primaryType": "ContentShare",
//   "message": {
//     "dataHash": "0xabc123...",
//     "publisher": "0x用户地址",
//     "shareId": "123",
//     "metadata": "{...}"
//   },
//   "contractAddress": "0x477c1FC569eCefE56a4e3D54616CC83AFB7d02E3"
// }
```

### 3. 用户使用 MetaMask 签名

```javascript
// 3.1 请求用户连接 MetaMask
const accounts = await window.ethereum.request({ method: 'eth_requestAccounts' });
const userAddress = accounts[0];

// 3.2 使用 EIP-712 签名
const signature = await window.ethereum.request({
  method: 'eth_signTypedData_v4',
  params: [
    userAddress,
    JSON.stringify({
      domain,
      types,
      primaryType,
      message
    })
  ]
});

console.log('用户签名:', signature);
```

### 4. 调用 Polygon 合约

```javascript
import { ethers } from 'ethers';

// 4.1 连接到 Polygon 网络
const provider = new ethers.providers.Web3Provider(window.ethereum);
await provider.send('wallet_switchEthereumChain', [{ chainId: '0x89' }]); // Polygon 主网
const signer = provider.getSigner();

// 4.2 合约 ABI (只需要 recordShare 函数)
const contractABI = [
  "function recordShare(bytes32 dataHash, address publisher, uint256 shareId, string metadata, bytes signature) external"
];

// 4.3 创建合约实例
const contract = new ethers.Contract(contractAddress, contractABI, signer);

// 4.4 调用合约
try {
  const tx = await contract.recordShare(
    message.dataHash,      // bytes32
    message.publisher,     // address
    message.shareId,       // uint256
    message.metadata,      // string
    signature              // bytes
  );
  
  console.log('交易已发送:', tx.hash);
  
  // 4.5 等待交易确认
  const receipt = await tx.wait();
  console.log('交易已确认:', receipt.transactionHash);
  
} catch (error) {
  if (error.code === 'INSUFFICIENT_FUNDS') {
    alert('POL 余额不足，无法支付 Gas 费用');
  } else if (error.code === 4001) {
    alert('用户取消了交易');
  } else {
    alert('交易失败: ' + error.message);
  }
  throw error;
}
```

### 5. 提交交易哈希给后端验证

```javascript
// 5.1 后端验证交易并发放 WEE 奖励
const verifyResponse = await axios.post(`/api/content-shares/${shareId}/polygon-proof`, {
  txHash: receipt.transactionHash
});

if (verifyResponse.data.code === 200) {
  alert('✅ Polygon 存证成功！WEE 奖励已发放到您的账户');
} else {
  alert('❌ 验证失败: ' + verifyResponse.data.message);
}
```

---

## 🎨 完整的 Vue 组件示例

```vue
<template>
  <div class="polygon-proof-button">
    <el-button 
      v-if="!proofStatus" 
      @click="startProof" 
      :loading="loading"
      type="primary"
    >
      {{ loading ? '存证中...' : '提交到 Polygon 存证' }}
    </el-button>
    <div v-else-if="proofStatus === 'success'" class="proof-success">
      ✅ 已存证，WEE 奖励已发放
    </div>
    <div v-else-if="proofStatus === 'failed'" class="proof-failed">
      ❌ 存证失败
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ethers } from 'ethers';
import axios from 'axios';
import { ElMessage } from 'element-plus';

const props = defineProps({
  shareId: {
    type: Number,
    required: true
  }
});

const loading = ref(false);
const proofStatus = ref(null); // null | 'success' | 'failed'

const contractABI = [
  "function recordShare(bytes32 dataHash, address publisher, uint256 shareId, string metadata, bytes signature) external"
];

async function startProof() {
  loading.value = true;
  
  try {
    // Step 1: 检查 MetaMask
    if (!window.ethereum) {
      ElMessage.error('请先安装 MetaMask 插件');
      return;
    }
    
    // Step 2: 连接钱包
    const accounts = await window.ethereum.request({ method: 'eth_requestAccounts' });
    const userAddress = accounts[0];
    
    // Step 3: 切换到 Polygon 主网
    try {
      await window.ethereum.request({
        method: 'wallet_switchEthereumChain',
        params: [{ chainId: '0x89' }] // Polygon 主网
      });
    } catch (switchError) {
      if (switchError.code === 4902) {
        // 网络未添加，引导用户添加
        await window.ethereum.request({
          method: 'wallet_addEthereumChain',
          params: [{
            chainId: '0x89',
            chainName: 'Polygon Mainnet',
            nativeCurrency: { name: 'POL', symbol: 'POL', decimals: 18 },
            rpcUrls: ['https://polygon-rpc.com/'],
            blockExplorerUrls: ['https://polygonscan.com/']
          }]
        });
      } else {
        throw switchError;
      }
    }
    
    // Step 4: 获取签名数据
    const signDataResponse = await axios.get(`/api/content-shares/${props.shareId}/polygon-sign-data`);
    const { domain, types, primaryType, message, contractAddress } = signDataResponse.data;
    
    // Step 5: 用户签名
    const signature = await window.ethereum.request({
      method: 'eth_signTypedData_v4',
      params: [userAddress, JSON.stringify({ domain, types, primaryType, message })]
    });
    
    // Step 6: 调用合约
    const provider = new ethers.providers.Web3Provider(window.ethereum);
    const signer = provider.getSigner();
    const contract = new ethers.Contract(contractAddress, contractABI, signer);
    
    const tx = await contract.recordShare(
      message.dataHash,
      message.publisher,
      message.shareId,
      message.metadata,
      signature
    );
    
    ElMessage.info('交易已发送，等待确认...');
    const receipt = await tx.wait();
    
    // Step 7: 提交给后端验证
    const verifyResponse = await axios.post(`/api/content-shares/${props.shareId}/polygon-proof`, {
      txHash: receipt.transactionHash
    });
    
    if (verifyResponse.data.code === 200) {
      proofStatus.value = 'success';
      ElMessage.success('✅ Polygon 存证成功！WEE 奖励已发放');
    } else {
      proofStatus.value = 'failed';
      ElMessage.error('验证失败: ' + verifyResponse.data.message);
    }
    
  } catch (error) {
    console.error('Polygon 存证失败:', error);
    proofStatus.value = 'failed';
    
    if (error.code === 'INSUFFICIENT_FUNDS') {
      ElMessage.error('POL 余额不足，无法支付 Gas 费用');
    } else if (error.code === 4001) {
      ElMessage.warning('您取消了交易');
    } else {
      ElMessage.error('存证失败: ' + (error.message || '未知错误'));
    }
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.proof-success {
  color: #67c23a;
  font-weight: bold;
}

.proof-failed {
  color: #f56c6c;
  font-weight: bold;
}
</style>
```

---

## 🚨 常见问题

### Q1: 用户没有 POL 代币怎么办？

**A**: 前端需要在调用合约前检查用户的 POL 余额，并提示用户：

```javascript
const balance = await provider.getBalance(userAddress);
const estimatedGas = await contract.estimateGas.recordShare(...);
const gasPrice = await provider.getGasPrice();
const requiredBalance = estimatedGas.mul(gasPrice);

if (balance.lt(requiredBalance)) {
  alert('您的 POL 余额不足，需要至少 ' + ethers.utils.formatEther(requiredBalance) + ' POL');
  return;
}
```

### Q2: 用户取消签名怎么办？

**A**: 捕获 `error.code === 4001`，提示用户需要签名才能获得 WEE 奖励。

### Q3: 交易失败（revert）怎么办？

**A**: 可能的原因：
- ShareId 已被使用
- 内容哈希已被记录
- 签名验证失败

前端可以解析 revert 原因并友好提示用户。

---

## 📌 TODO 清单

- [ ] 前端安装 `ethers.js`: `npm install ethers`
- [ ] 创建 Polygon 存证按钮组件
- [ ] 集成到内容上传流程
- [ ] 添加 Gas 费用估算提示
- [ ] 添加交易状态追踪
- [ ] 处理各种错误场景
- [ ] 测试完整流程

---

## 🎉 总结

新方案让用户自己签名和发送交易，符合 Web3 去中心化理念，同时后端通过 WEE 奖励激励用户参与，既节省了后端成本，又防止了恶意攻击。

前端需要做的改动不大，主要是集成 MetaMask 签名和 ethers.js 合约调用，用户体验也更好（自己掌控交易）。

