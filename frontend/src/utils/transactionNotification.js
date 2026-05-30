import Vue from 'vue'
import TransactionNotification from '@/components/TransactionNotification.vue'

// 创建一个单例实例
let instance = null

const TransactionNotificationConstructor = Vue.extend(TransactionNotification)

function createInstance() {
  instance = new TransactionNotificationConstructor()
  instance.$mount()
  document.body.appendChild(instance.$el)
  return instance
}

function getInstance() {
  if (!instance) {
    instance = createInstance()
  }
  return instance
}

// 导出便捷方法
export default {
  // 显示交易提交中
  pending(message = '正在提交交易...') {
    const inst = getInstance()
    inst.show({
      status: 'pending',
      title: '交易提交中',
      message
    })
    return inst
  },
  
  // 显示交易确认中
  confirming(txHash, message = '等待区块链确认...') {
    const inst = getInstance()
    inst.show({
      status: 'confirming',
      title: '等待确认',
      message,
      txHash
    })
    return inst
  },
  
  // 更新为确认中状态
  updateToConfirming(txHash, message = '交易已提交，等待确认...') {
    if (instance) {
      instance.update({
        status: 'confirming',
        title: '等待确认',
        message,
        txHash
      })
    }
    return instance
  },
  
  // 显示交易成功
  success(txHash, message = '交易成功完成！') {
    const inst = getInstance()
    inst.show({
      status: 'success',
      title: '交易成功',
      message,
      txHash,
      duration: 5000
    })
    return inst
  },
  
  // 更新为成功状态
  updateToSuccess(message = '交易成功完成！') {
    if (instance) {
      instance.update({
        status: 'success',
        title: '交易成功',
        message
      })
    }
    return instance
  },
  
  // 显示交易失败
  error(message = '交易失败，请重试') {
    const inst = getInstance()
    inst.show({
      status: 'error',
      title: '交易失败',
      message,
      duration: 5000
    })
    return inst
  },
  
  // 更新为失败状态
  updateToError(message = '交易失败，请重试') {
    if (instance) {
      instance.update({
        status: 'error',
        title: '交易失败',
        message
      })
    }
    return instance
  },
  
  // 关闭通知
  close() {
    if (instance) {
      instance.close()
    }
  },
  
  // 获取实例
  getInstance
}

