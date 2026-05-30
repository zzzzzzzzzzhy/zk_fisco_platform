const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  
  // 生产环境配置
  productionSourceMap: false,
  
  // 开发服务器配置
  devServer: {
    port: 8084,
    open: false,
    compress: true,
    
    proxy: {
      '/api': {
        target: process.env.VUE_APP_API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        ws: true
      },
      '/coffee-api': {
        target: process.env.VUE_APP_COFFEE_API_PROXY_TARGET || 'http://localhost:9090',
        changeOrigin: true,
        ws: true,
        pathRewrite: {
          '^/coffee-api': ''
        }
      }
    }
  },
  
  // Webpack 优化配置
  configureWebpack: {
    optimization: {
      splitChunks: {
        chunks: 'all',
        cacheGroups: {
          // Element UI 单独打包
          elementUI: {
            name: 'chunk-elementUI',
            test: /[\\/]node_modules[\\/]element-ui[\\/]/,
            priority: 20
          },
          
          // Ethers.js 单独打包
          ethers: {
            name: 'chunk-ethers',
            test: /[\\/]node_modules[\\/]ethers[\\/]/,
            priority: 20
          },
          
          // Vue 全家桶
          vue: {
            name: 'chunk-vue',
            test: /[\\/]node_modules[\\/](vue|vue-router|vuex)[\\/]/,
            priority: 20
          },
          
          // 其他第三方库
          vendors: {
            name: 'chunk-vendors',
            test: /[\\/]node_modules[\\/]/,
            priority: 10,
            minChunks: 2
          },
          
          // 公共代码
          common: {
            name: 'chunk-common',
            minChunks: 2,
            priority: 5
          }
        }
      },
      
      // 运行时代码单独打包
      runtimeChunk: {
        name: 'runtime'
      }
    }
  },
  
  // 链式配置（简化版）
  chainWebpack: config => {
    // 删除 preload 和 prefetch（避免配置冲突）
    config.plugins.delete('preload')
    config.plugins.delete('prefetch')
    
    // 生产环境优化
    if (process.env.NODE_ENV === 'production') {
      config.module
        .rule('images')
        .set('parser', {
          dataUrlCondition: {
            maxSize: 10240 // 10KB 以下转 base64
          }
        })
    }
  }
})
