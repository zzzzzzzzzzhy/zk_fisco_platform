# 友好错误提示使用指南

## 🎯 问题

**之前的错误提示**：
```
user rejected transaction (action="sendTransaction", 
transaction={"data":"0x095ea7b3000000000...", 
code=ACTION_REJECTED, version=providers/5.8.0)
```
用户：❓❓❓ 完全看不懂！

**现在的错误提示**：
```
✅ 交易已取消
您在 MetaMask 中取消了交易
```
用户：😊 清楚明了！

---

## 📖 基本用法

### 方法 1：在组件中使用（推荐）

```javascript
export default {
  methods: {
    async handleVote() {
      try {
        const tx = await governorContract.castVote(proposalId, support)
        await tx.wait()
        this.$message.success('投票成功！')
      } catch (error) {
        // ✅ 使用智能错误处理器
        this.$handleTxError(error)
        // 自动显示友好提示：
        // "余额不足" 或 "交易已取消" 等
      }
    }
  }
}
```

### 方法 2：导入使用

```javascript
import { handleTransactionError } from '@/utils/errorHandler'

async function vote() {
  try {
    // ... 执行交易
  } catch (error) {
    handleTransactionError(error)
  }
}
```

---

## 🔧 不同场景的使用

### 1. 区块链交易错误

```javascript
// 投票、转账、合约调用等
async handleTransaction() {
  try {
    const tx = await contract.someFunction()
    await tx.wait()
  } catch (error) {
    this.$handleTxError(error)
    // 自动识别并显示友好提示
  }
}
```

**支持的错误类型**：
- ✅ 用户取消交易 → "交易已取消"
- ✅ 余额不足 → "余额不足，请充值 POL"
- ✅ Gas 不足 → "Gas 不足，请增加 Gas Limit"
- ✅ 合约执行失败 → "交易失败，请检查参数"
- ✅ 网络错误 → "网络连接失败"

---

### 2. API 请求错误

```javascript
async fetchData() {
  try {
    const response = await this.$http.get('/api/proposals')
  } catch (error) {
    this.$handleApiError(error)
    // 自动识别并显示友好提示
  }
}
```

**支持的错误类型**：
- ✅ 401 → "登录已过期，请重新登录"
- ✅ 403 → "您没有权限访问"
- ✅ 404 → "资源不存在"
- ✅ 500 → "服务器错误，请稍后重试"
- ✅ 网络错误 → "无法连接到服务器"

---

### 3. 合约调用错误

```javascript
async callContract() {
  try {
    const result = await contract.viewFunction()
  } catch (error) {
    this.$handleContractError(error)
    // 自动提取合约 revert 原因
  }
}
```

**支持的错误类型**：
- ✅ "already cast" → "您已经投过票了"
- ✅ "proposal not active" → "提案还未开始投票"
- ✅ "insufficient balance" → "WEE 代币余额不足"
- ✅ "already claimed" → "您已经领取过此奖励"
- ✅ "not authorized" → "您没有权限执行此操作"

---

### 4. 通用错误处理

```javascript
async doSomething() {
  try {
    // ... 任何操作
  } catch (error) {
    this.$handleError(error)
    // 通用错误处理
  }
}
```

---

## 🎨 错误提示样式

### 不同类型的提示

```javascript
// ❌ 错误（红色）
this.$handleError('insufficient funds')
// 显示：余额不足，请充值 POL 代币后重试

// ⚠️ 警告（橙色）
this.$handleError('already claimed')
// 显示：您已经领取过此奖励

// ℹ️ 信息（蓝色）
this.$handleError('ACTION_REJECTED')
// 显示：您取消了本次操作
```

### 自定义错误消息

```javascript
try {
  // ... 操作
} catch (error) {
  this.$handleError(error, {
    customMessage: '投票失败，请稍后重试'
  })
}
```

### 不显示提示（仅处理）

```javascript
const errorInfo = this.$handleError(error, {
  showMessage: false
})

console.log(errorInfo)
// {
//   title: '余额不足',
//   message: '您的钱包余额不足',
//   type: 'error',
//   solution: '请充值 POL 代币后重试'
// }
```

---

## 📊 错误提示对比

### 案例 1：用户取消交易

**优化前**：
```
Error: user rejected transaction (action="sendTransaction", 
transaction={...}, code=ACTION_REJECTED, version=providers/5.8.0)
```

**优化后**：
```
ℹ️ 交易已取消
您在 MetaMask 中取消了交易
```

---

### 案例 2：余额不足

**优化前**：
```
Error: insufficient funds for gas * price + value (error={"code":-32000,"message":"insufficient funds"}, ...)
```

**优化后**：
```
❌ 余额不足
您的钱包余额不足以支付 Gas 费用
💡 请充值 POL 代币后重试
```

---

### 案例 3：重复投票

**优化前**：
```
Error: execution reverted: GovernorVotingSimple: vote already cast (error={"code":3,"message":"execution reverted..."}, ...)
```

**优化后**：
```
⚠️ 重复投票
您已经投过票了
```

---

### 案例 4：网络错误

**优化前**：
```
Error: could not detect network (event="noNetwork", code=NETWORK_ERROR, version=providers/5.8.0)
```

**优化后**：
```
❌ 网络错误
无法连接到区块链网络
💡 请检查网络连接或切换 RPC 节点
```

---

## 🚀 实际应用示例

### 投票功能

```vue
<template>
  <el-button @click="handleVote">投票</el-button>
</template>

<script>
export default {
  methods: {
    async handleVote() {
      try {
        // 1. 显示交易提交中
        this.$txNotify.pending('正在提交投票...')
        
        // 2. 发送交易
        const tx = await this.$web3.castVote(proposalId, 1)
        
        // 3. 等待确认
        this.$txNotify.updateToConfirming(tx.hash, '等待确认...')
        await tx.wait()
        
        // 4. 成功
        this.$txNotify.updateToSuccess('投票成功！')
        
      } catch (error) {
        // 5. 友好的错误提示
        this.$handleTxError(error)
        // 可能显示：
        // - "交易已取消"（用户取消）
        // - "余额不足"（Gas 不足）
        // - "您已经投过票了"（重复投票）
      }
    }
  }
}
</script>
```

### 签到功能

```vue
<script>
export default {
  methods: {
    async handleCheckin() {
      try {
        this.$txNotify.pending('正在签到...')
        
        const tx = await this.$web3.dailyCheckin()
        this.$txNotify.updateToConfirming(tx.hash)
        await tx.wait()
        
        this.$txNotify.updateToSuccess('签到成功！获得 WEE 奖励')
        
      } catch (error) {
        this.$handleTxError(error)
        // 可能显示：
        // - "您已经领取过此奖励"（今天已签到）
        // - "钱包未连接"（未连接 MetaMask）
      }
    }
  }
}
</script>
```

### 内容发布

```vue
<script>
export default {
  methods: {
    async publishContent() {
      try {
        // 上传到后端
        const content = await this.$http.post('/api/content', data)
        
        // 区块链存证
        this.$txNotify.pending('正在存证到 Polygon...')
        const tx = await this.$web3.publish(content.id)
        this.$txNotify.updateToConfirming(tx.hash)
        await tx.wait()
        
        this.$txNotify.updateToSuccess('发布成功！')
        
      } catch (error) {
        // 智能判断是 API 错误还是交易错误
        if (error.response) {
          this.$handleApiError(error)
        } else {
          this.$handleTxError(error)
        }
      }
    }
  }
}
</script>
```

---

## 💡 最佳实践

### 1. 总是处理错误

```javascript
// ❌ 不好：忽略错误
async function vote() {
  const tx = await contract.vote()
  await tx.wait()
}

// ✅ 好：处理错误
async function vote() {
  try {
    const tx = await contract.vote()
    await tx.wait()
    this.$message.success('投票成功')
  } catch (error) {
    this.$handleTxError(error)
  }
}
```

### 2. 使用专用处理器

```javascript
// 区块链交易 → 使用 $handleTxError
// API 请求 → 使用 $handleApiError
// 合约调用 → 使用 $handleContractError
// 通用错误 → 使用 $handleError
```

### 3. 配合交易通知使用

```javascript
try {
  this.$txNotify.pending('提交中...')
  const tx = await contract.call()
  this.$txNotify.updateToConfirming(tx.hash)
  await tx.wait()
  this.$txNotify.updateToSuccess('成功！')
} catch (error) {
  this.$handleTxError(error)
  // 错误处理器会自动关闭交易通知并显示错误
}
```

---

## 🎊 效果

**用户体验提升**：
- 😊 清晰易懂的错误提示
- 💡 包含解决方案
- 🎨 美观的视觉效果
- ⚡ 自动识别错误类型

**开发体验提升**：
- ✅ 统一的错误处理
- 🔧 简单易用的 API
- 📝 完整的类型支持
- 🐛 更好的调试信息

---

## 📚 完整API

```javascript
// 通用错误处理
this.$handleError(error, options)

// 区块链交易错误
this.$handleTxError(error, customMessage)

// API 请求错误
this.$handleApiError(error)

// 合约调用错误
this.$handleContractError(error)
```

**Options 参数**：
```javascript
{
  showMessage: true,      // 是否显示提示
  duration: 5000,         // 提示持续时间（毫秒）
  customMessage: null     // 自定义消息
}
```

---

**现在所有错误都会显示友好的提示！** ✨

