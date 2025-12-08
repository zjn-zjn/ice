<p align="center">
  <img width="128" alt="Ice Logo" src="https://user-images.githubusercontent.com/33447125/151098049-72aaf8d1-b759-4d84-bf6b-1a2260033582.png">
</p>

<h1 align="center">Ice</h1>

<p align="center">
  <strong>🧊 Lightweight Visual Rule Engine for Business Orchestration</strong>
</p>

<p align="center">
  Committed to solving flexible and complex hard-coded problems
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0.html"><img src="https://img.shields.io/badge/license-Apache%202-4EB1BA.svg" alt="License"></a>
  <a href="https://central.sonatype.com/artifact/com.waitmoon.ice/ice"><img src="https://img.shields.io/maven-central/v/com.waitmoon.ice/ice.svg" alt="Maven Central"></a>
  <a href="https://pkg.go.dev/github.com/waitmoon/ice/sdks/go"><img src="https://pkg.go.dev/badge/github.com/waitmoon/ice/sdks/go.svg" alt="Go Reference"></a>
  <a href="https://pypi.org/project/ice-rules/"><img src="https://img.shields.io/pypi/v/ice-rules.svg" alt="PyPI"></a>
  <a href="https://hub.docker.com/r/waitmoon/ice-server"><img src="https://img.shields.io/docker/pulls/waitmoon/ice-server" alt="Docker Pulls"></a>
</p>

<p align="center">
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-features">Features</a> •
  <a href="#-documentation">Documentation</a> •
  <a href="#-use-cases">Use Cases</a> •
  <a href="#-whos-using-ice">Who's Using</a>
</p>

<p align="center">
  <a href="#-快速开始">🇨🇳 中文</a>
</p>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🎯 **Visual Rule Orchestration** | Innovative tree-based orchestration with web visual configuration interface |
| ⚡ **High Performance** | Pure in-memory computation with millisecond response time |
| 🐳 **Zero Dependencies** | No MySQL, ZooKeeper required. Docker one-click deployment in 5 seconds |
| 🌍 **Multi-Language SDKs** | Java, Go, Python SDKs with full feature parity |
| 🔄 **Hot Reload** | Configuration changes take effect in seconds without restart |
| 📦 **Lightweight** | JSON file storage, version control friendly |

## 🚀 Quick Start

### Step 1: Deploy Ice Server

```bash
docker run -d --name ice-server \
  -p 8121:8121 \
  -v ./ice-data:/app/ice-data \
  waitmoon/ice-server:latest
```

Or use Docker Compose:

```bash
docker-compose up -d
```

Visit http://localhost:8121 to access the visual configuration interface.

### Step 2: Integrate Ice Client SDK

<details open>
<summary><b>Java (SpringBoot 3.x)</b></summary>

```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-spring-boot-starter-3x</artifactId>
  <version>2.0.1</version>
</dependency>
```

```yaml
# application.yml
ice:
  app: 1
  storage:
    path: ./ice-data  # Same as Server
  scan: com.your.package
```

</details>

<details>
<summary><b>Java (SpringBoot 2.x)</b></summary>

```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-spring-boot-starter-2x</artifactId>
  <version>2.0.1</version>
</dependency>
```

</details>

<details>
<summary><b>Go</b></summary>

```bash
go get github.com/waitmoon/ice/sdks/go
```

```go
import ice "github.com/waitmoon/ice/sdks/go"

client, _ := ice.NewClient(1, "./ice-data")
client.Start()
defer client.Destroy()
```

</details>

<details>
<summary><b>Python</b></summary>

```bash
pip install ice-rules
```

```python
import ice

client = ice.FileClient(app=1, storage_path="./ice-data")
client.start()
```

</details>

### Step 3: Execute Rules

```java
// Java
IcePack pack = new IcePack();
pack.setIceId(1L);
pack.setRoam(new IceRoam().put("uid", 12345));
Ice.syncProcess(pack);
```

```go
// Go
pack := ice.NewPack().SetIceId(1)
pack.Roam.Put("uid", 12345)
ice.SyncProcess(context.Background(), pack)
```

```python
# Python
pack = ice.Pack(ice_id=1)
pack.roam.put("uid", 12345)
ice.sync_process(pack)
```

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Shared Storage (ice-data/)               │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────┐    │
│  │  apps/  │  │ bases/  │  │ confs/  │  │  versions/  │    │
│  └─────────┘  └─────────┘  └─────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────────┘
        ▲ Write                              ▲ Read (Poll)
        │                                    │
┌───────┴───────┐                   ┌────────┴────────┐
│   Ice Server  │                   │   Ice Client    │
│  (Config UI)  │                   │ (Rule Engine)   │
│               │                   │                 │
│ • Web UI      │                   │ • Poll version  │
│ • Rule editor │                   │ • Hot reload    │
│ • Publishing  │                   │ • In-memory exec│
└───────────────┘                   └─────────────────┘
```

## 📚 Documentation

| Language | Links |
|----------|-------|
| English | [📖 Documentation](https://waitmoon.com/en/) · [🚀 Quick Start](https://waitmoon.com/en/guide/getting-started.html) · [🐹 Go SDK](https://waitmoon.com/en/guide/go-sdk.html) · [🐍 Python SDK](https://waitmoon.com/en/guide/python-sdk.html) |
| 中文 | [📖 文档](https://waitmoon.com/) · [🚀 快速开始](https://waitmoon.com/guide/getting-started.html) · [🎥 视频教程](https://www.bilibili.com/video/BV1Q34y1R7KF) |

**Live Demo**: [https://eg.waitmoon.com](https://eg.waitmoon.com)

## 💡 Use Cases

| Scenario | Description |
|----------|-------------|
| 🎁 **Marketing Campaigns** | Flexible configuration for coupons, discounts, group buying rules |
| 💰 **Risk Control** | Credit risk assessment, anti-fraud, real-time decision engine |
| 🔐 **Access Control** | Dynamic permission management, role configuration |
| 📊 **Process Orchestration** | Ticket routing, approval workflows, state machine management |

## 🏢 Who's Using Ice

<p align="center">
  <img height="40" src="https://waitmoon.com/images/user/iflytek.png" alt="iFlytek">
  &nbsp;&nbsp;&nbsp;
  <img height="40" src="https://waitmoon.com/images/user/xima.png" alt="Ximalaya">
  &nbsp;&nbsp;&nbsp;
  <img height="40" src="https://waitmoon.com/images/user/agora.png" alt="Agora">
  &nbsp;&nbsp;&nbsp;
  <img height="40" src="https://waitmoon.com/images/user/h3c.png" alt="H3C">
  &nbsp;&nbsp;&nbsp;
  <img height="40" src="https://waitmoon.com/images/user/tuhu.png" alt="Tuhu">
</p>

> Using Ice? [Let us know!](https://github.com/zjn-zjn/ice/issues/new)

## 📄 License

[Apache License 2.0](LICENSE)

---

<a id="-快速开始"></a>

# 🇨🇳 中文文档

## ✨ 特性

| 特性 | 描述 |
|------|------|
| 🎯 **可视化规则编排** | 创新的树形编排，Web可视化配置界面 |
| ⚡ **高性能** | 纯内存计算，毫秒级响应 |
| 🐳 **零依赖** | 无需MySQL、ZooKeeper，Docker一键部署 |
| 🌍 **多语言SDK** | Java、Go、Python SDK功能完全对等 |
| 🔄 **热更新** | 配置变更秒级生效，无需重启 |
| 📦 **轻量级** | JSON文件存储，版本控制友好 |

## 🚀 快速开始

### 第一步：部署 Ice Server

```bash
docker run -d --name ice-server \
  -p 8121:8121 \
  -v ./ice-data:/app/ice-data \
  waitmoon/ice-server:latest
```

访问 http://localhost:8121 进入可视化配置界面。

### 第二步：集成 Ice Client SDK

**Java (SpringBoot 3.x)**

```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-spring-boot-starter-3x</artifactId>
  <version>2.0.1</version>
</dependency>
```

```yaml
# application.yml
ice:
  app: 1
  storage:
    path: ./ice-data  # 与Server共享同一目录
  scan: com.your.package
```

**Go**

```bash
go get github.com/waitmoon/ice/sdks/go
```

**Python**

```bash
pip install ice-rules
```

### 第三步：执行规则

```java
IcePack pack = new IcePack();
pack.setIceId(1L);
pack.setRoam(new IceRoam().put("uid", 12345));
Ice.syncProcess(pack);
```

## 📚 文档链接

- [📖 完整文档](https://waitmoon.com/)
- [🚀 快速开始](https://waitmoon.com/guide/getting-started.html)
- [🎥 视频介绍](https://www.bilibili.com/video/BV1hg411A7jx)
- [🎥 开发配置视频](https://www.bilibili.com/video/BV1Q34y1R7KF)
- [💬 交流群](https://waitmoon.com/community/community.html)

**在线体验**: [https://eg.waitmoon.com](https://eg.waitmoon.com)

## 🤝 社区

- [GitHub Issues](https://github.com/zjn-zjn/ice/issues)
- [Discussions](https://github.com/zjn-zjn/ice/discussions)

---

<p align="center">
  Made with ❤️ by <a href="https://waitmoon.com">WaitMoon</a>
</p>
