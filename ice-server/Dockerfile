# 运行阶段（使用已打包的 jar）
FROM eclipse-temurin:8-jre
LABEL maintainer="waitmoon"
LABEL version="2.0.0"
LABEL description="Ice Server - Rule Engine Configuration Server"

WORKDIR /app

# curl 已包含在 eclipse-temurin 镜像中

# 复制打包好的 jar
COPY target/ice-server.jar /app/ice-server.jar

# 创建数据目录
RUN mkdir -p /app/ice-data

# 环境变量
ENV JAVA_OPTS="-Xms512m -Xmx512m"
ENV ICE_STORAGE_PATH=/app/ice-data
ENV SERVER_PORT=8121

# 暴露端口
EXPOSE 8121

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dice.storage.path=$ICE_STORAGE_PATH -Dserver.port=$SERVER_PORT -jar /app/ice-server.jar"]
