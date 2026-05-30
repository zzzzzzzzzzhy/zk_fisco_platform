#
# 精简版 Dockerfile：不再在容器内执行 Maven 构建，而是复用宿主机已构建好的 JAR。
# 这样可以避免在 Docker 构建过程中重复下载依赖，解决构建特别慢/经常超时的问题。
#
# 使用前置条件：
#   在宿主机（WSL）先执行：
#     cd /data/Dapp_Share_Platform/competition-platform/backend
#     mvn clean package -DskipTests
#   构建成功后会生成 target/competition-platform.jar，然后再执行：
#     cd ../docker
#     docker compose build app
#

FROM eclipse-temurin:17-jre
WORKDIR /app

# 安装 Docker CLI（用于本地 Groth16 转换）
RUN apt-get update \
    && apt-get install -y docker.io \
    && rm -rf /var/lib/apt/lists/*

# 直接复制宿主机已经打好的 JAR 包到镜像中
COPY target/competition-platform.jar /app/competition-platform.jar
# Rollup 证明生成器
COPY prover/rollup-prove /app/prover/rollup-prove
COPY prover/r0vm /app/prover/r0vm
RUN chmod +x /app/prover/rollup-prove /app/prover/r0vm

EXPOSE 8080

# 添加 JVM 参数解决 MyBatis-Plus 在 Docker 中 "Unable to retrieve localhost" 的问题
ENTRYPOINT ["java", \
            "-Djava.net.preferIPv4Stack=true", \
            "-Djava.net.preferIPv6Addresses=false", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", \
            "-Dspring.profiles.active=prod", \
            "/app/competition-platform.jar"]
