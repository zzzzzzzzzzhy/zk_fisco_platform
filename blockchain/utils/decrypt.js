const CryptoJS = require('crypto-js');

/**
 * 解密私钥
 * @param {string} encryptedPrivateKey - 加密的私钥
 * @param {string} password - 解密密码
 * @returns {string} 解密后的私钥
 */
function decryptPrivateKey(encryptedPrivateKey, password) {
    try {
        const decrypted = CryptoJS.AES.decrypt(encryptedPrivateKey, password);
        const privateKey = decrypted.toString(CryptoJS.enc.Utf8);

        if (!privateKey || privateKey.length !== 64) {
            throw new Error('解密失败：私钥格式不正确或密码错误');
        }

        return privateKey;
    } catch (error) {
        throw new Error(`私钥解密失败: ${error.message}`);
    }
}

/**
 * 获取解密后的私钥（从环境变量）
 * @param {string} password - 解密密码
 * @returns {string} 解密后的私钥
 */
function getDecryptedPrivateKey(password) {
    const encryptedPrivateKey = process.env.ENCRYPTED_PRIVATE_KEY;

    if (!encryptedPrivateKey) {
        throw new Error('未找到 ENCRYPTED_PRIVATE_KEY 环境变量');
    }

    return decryptPrivateKey(encryptedPrivateKey, password);
}

module.exports = {
    decryptPrivateKey,
    getDecryptedPrivateKey
};