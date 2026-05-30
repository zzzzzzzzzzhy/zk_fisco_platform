/**
 * 文件哈希计算工具
 * 使用 Web Crypto API 计算文件的 SHA-256 哈希值
 */

import { sha256Hex } from './sha256'

/**
 * 计算文件的 SHA-256 哈希值
 * @param {File} file - 要计算哈希的文件对象
 * @returns {Promise<string>} - 返回十六进制格式的哈希字符串
 */
export async function calculateFileHash(file) {
  return new Promise((resolve, reject) => {
    if (!file) {
      reject(new Error('文件对象为空'))
      return
    }

    const reader = new FileReader()

    reader.onload = async (event) => {
      try {
        const arrayBuffer = event.target.result

        if (!arrayBuffer || arrayBuffer.byteLength === 0) {
          reject(new Error('文件内容为空'))
          return
        }

        console.log('开始计算文件哈希，文件大小:', file.size, 'bytes')
        console.log('ArrayBuffer 大小:', arrayBuffer.byteLength, 'bytes')

        let hashHex
        // 优先使用 Web Crypto API（通常要求 HTTPS / localhost 安全上下文）
        if (window.crypto && window.crypto.subtle && (window.isSecureContext || window.location.hostname === 'localhost')) {
          const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer)
          const hashArray = Array.from(new Uint8Array(hashBuffer))
          hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
        } else {
          // 降级：纯 JS 实现，避免在 HTTP/老浏览器下直接失败
          hashHex = sha256Hex(arrayBuffer)
        }

        console.log('文件哈希计算成功:', hashHex)
        resolve(hashHex)
      } catch (error) {
        console.error('文件哈希计算错误:', error)
        reject(new Error('哈希计算失败: ' + error.message))
      }
    }

    reader.onerror = () => {
      console.error('文件读取失败')
      reject(new Error('文件读取失败'))
    }

    // 读取文件为 ArrayBuffer
    reader.readAsArrayBuffer(file)
  })
}

/**
 * 计算字符串的 SHA-256 哈希值
 * @param {string} str - 要计算哈希的字符串
 * @returns {Promise<string>} - 返回十六进制格式的哈希字符串
 */
export async function calculateStringHash(str) {
  const encoder = new TextEncoder()
  const data = encoder.encode(str)
  if (window.crypto && window.crypto.subtle && (window.isSecureContext || window.location.hostname === 'localhost')) {
    const hashBuffer = await crypto.subtle.digest('SHA-256', data)
    const hashArray = Array.from(new Uint8Array(hashBuffer))
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
  }
  return sha256Hex(data)
}
