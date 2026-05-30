# 第一阶段：构建前端
FROM docker.1ms.run/node:18 AS build
WORKDIR /app

# 复制 package.json 和 package-lock.json
COPY package*.json ./

# 安装依赖
RUN npm install

# 允许通过构建参数注入 Vue 环境变量（编译期生效）
ARG VUE_APP_IPFS_PUBLIC_GATEWAY
ENV VUE_APP_IPFS_PUBLIC_GATEWAY=${VUE_APP_IPFS_PUBLIC_GATEWAY}

# 复制源代码
COPY . .

# 构建生产版本
RUN npm run build

# 第二阶段：Nginx 提供静态文件
FROM docker.1ms.run/nginx:alpine
WORKDIR /usr/share/nginx/html

# 删除 Nginx 默认页面
RUN rm -rf ./*

# 从构建阶段复制打包好的文件
COPY --from=build /app/dist .

# 兼容旧的预加载路径，避免 /js/app.js 和 /js/chunk-vendors.js 404
RUN set -eux; \
    if [ -d /usr/share/nginx/html/js ]; then \
      cd /usr/share/nginx/html/js; \
      for name in app chunk-vendors; do \
        if [ ! -f "${name}.js" ]; then \
          target="$(ls ${name}.*.js 2>/dev/null | head -n 1 || true)"; \
          if [ -n "$target" ]; then \
            ln -s "$target" "${name}.js"; \
          fi; \
        fi; \
      done; \
    fi

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
