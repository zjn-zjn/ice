<p align="center">
  <img width="128" alt="Ice" src="https://user-images.githubusercontent.com/33447125/151098049-72aaf8d1-b759-4d84-bf6b-1a2260033582.png">
</p>

<h1 align="center">Ice</h1>

<p align="center">
  轻量级可视化规则引擎，致力于解决灵活繁复的硬编码问题
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0.html"><img src="https://img.shields.io/badge/license-Apache%202-blue.svg" alt="License"></a>
  <a href="https://central.sonatype.com/artifact/com.waitmoon.ice/ice"><img src="https://img.shields.io/maven-central/v/com.waitmoon.ice/ice.svg" alt="Maven Central"></a>
  <a href="https://pkg.go.dev/github.com/waitmoon/ice/sdks/go"><img src="https://pkg.go.dev/badge/github.com/waitmoon/ice/sdks/go.svg" alt="Go Reference"></a>
  <a href="https://pypi.org/project/ice-rules/"><img src="https://img.shields.io/pypi/v/ice-rules.svg" alt="PyPI"></a>
</p>

<p align="center">
  <a href="./README.md">English</a> · <a href="https://waitmoon.com/">文档</a> · <a href="https://eg.waitmoon.com">在线体验</a>
</p>

---

## 简介

Ice 是一个可视化规则引擎，用于解决复杂的业务硬编码问题。提供 Web 可视化配置界面，支持配置热更新，无需重启应用。

**核心特性**

- Web 可视化树形规则编排
- 零外部依赖（无需数据库或中间件）
- 多语言 SDK：Java、Go、Python
- 配置秒级热更新
- 纯内存执行，毫秒级响应

## 快速开始

### 部署 Server

```bash
docker run -d --name ice-server -p 8121:8121 \
  -v ./ice-data:/app/ice-data waitmoon/ice-server:latest
```

访问 http://localhost:8121 进入配置界面。

### 安装 SDK

**Java**
```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-spring-boot-starter-3x</artifactId>
  <version>2.0.1</version>
</dependency>
```

**Go**
```bash
go get github.com/waitmoon/ice/sdks/go
```

**Python**
```bash
pip install ice-rules
```

### 配置

```yaml
ice:
  app: 1
  storage:
    path: ./ice-data
```

> storage.path 需要与 Server 共享同一目录。

### 执行规则

```java
IcePack pack = new IcePack();
pack.setIceId(1L);
pack.setRoam(new IceRoam().put("uid", 12345));
Ice.syncProcess(pack);
```

## 文档

- [快速开始](https://waitmoon.com/guide/getting-started.html)
- [架构设计](https://waitmoon.com/advanced/architecture.html)
- [Go SDK 指南](https://waitmoon.com/guide/go-sdk.html)
- [Python SDK 指南](https://waitmoon.com/guide/python-sdk.html)
- [视频教程](https://www.bilibili.com/video/BV1Q34y1R7KF)

## 许可证

[Apache License 2.0](LICENSE)
