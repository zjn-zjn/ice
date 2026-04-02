<p align="center">
  <img width="140" alt="Ice" src="https://waitmoon.com/images/hero.svg">
</p>

<h1 align="center">Ice</h1>

<p align="center">
  轻量级、零依赖的可视化规则引擎，基于树形结构编排业务规则
</p>

<p align="center">
  <a href="https://github.com/zjn-zjn/ice/stargazers"><img src="https://img.shields.io/github/stars/zjn-zjn/ice?style=flat&logo=github" alt="GitHub Stars"></a>
  <a href="https://github.com/zjn-zjn/ice/releases"><img src="https://img.shields.io/github/v/release/zjn-zjn/ice" alt="GitHub Release"></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0.html"><img src="https://img.shields.io/badge/license-Apache%202-blue.svg" alt="License"></a>
  <a href="https://central.sonatype.com/artifact/com.waitmoon.ice/ice"><img src="https://img.shields.io/maven-central/v/com.waitmoon.ice/ice.svg" alt="Maven Central"></a>
  <a href="https://pkg.go.dev/github.com/zjn-zjn/ice/sdks/go"><img src="https://pkg.go.dev/badge/github.com/zjn-zjn/ice/sdks/go.svg" alt="Go Reference"></a>
  <a href="https://pypi.org/project/ice-rules/"><img src="https://img.shields.io/pypi/v/ice-rules.svg" alt="PyPI"></a>
  <a href="https://hub.docker.com/r/waitmoon/ice-server"><img src="https://img.shields.io/docker/pulls/waitmoon/ice-server" alt="Docker Pulls"></a>
</p>

<p align="center">
  <a href="https://waitmoon.com/">文档</a> &middot;
  <a href="https://eg.waitmoon.com">在线体验</a> &middot;
  <a href="https://waitmoon.com/guide/getting-started.html">快速开始</a> &middot;
  <a href="https://waitmoon.com/CHANGELOG.html">更新日志</a>
</p>

<p align="center">
  <a href="./README.md">English</a>
</p>

---

## Ice 是什么？

Ice 是一个采用全新设计理念的规则引擎：**规则以树形结构组织，而非链式或表格式**。树中的每个节点独立处理自己的逻辑——修改任何一个节点都不会对其他节点产生级联影响。节点之间通过共享的线程安全数据上下文 **Roam** 进行通信，而非直接相互引用。

这种设计使得业务规则可以通过可视化界面配置，秒级热更新生效，在内存中以亚毫秒级延迟执行——而这一切不需要任何数据库或中间件。

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://waitmoon.com/images/introduction/2-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="https://waitmoon.com/images/introduction/2-light.png">
  <img alt="Ice 规则示例" src="https://waitmoon.com/images/introduction/2-light.png" width="600">
</picture>

## 核心特性

|  | 特性 | 说明 |
|---|---|---|
| **🌲** | **树形编排** | 规则以树形结构组织，节点独立互不影响，修改单个节点不会产生级联效应 |
| **🎨** | **可视化编辑器** | 通过直观的 Web UI 配置规则，无需学习 DSL |
| **📦** | **零依赖** | 无需数据库、消息队列或服务注册中心——纯文件存储 |
| **🌍** | **多语言 SDK** | Java、Go、Python 功能完全对等 |
| **⚡** | **热更新** | 配置变更秒级生效，自动版本轮询，无需重启应用 |
| **🚀** | **高性能** | 纯内存执行，亚毫秒级延迟，零网络开销 |
| **♻️** | **节点复用** | 同一节点可在多棵规则树中共享使用 |
| **⚙️** | **并行执行** | 内置并行关系节点，支持子节点并发执行 |
| **🔍** | **Mock 调试** | 从 Web UI 远程触发规则执行，实时调试 |
| **🔀** | **Lane / 流量隔离** | 基于分支的规则隔离，支持 A/B 测试、灰度发布 |

## 工作原理

### 规则树

Ice 中的每条规则都是一棵**树**，由两类节点组成：

- **关系节点（Relation）** — 控制执行流程（类似逻辑运算符）
- **叶子节点（Leaf）** — 承载实际的业务逻辑

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://waitmoon.com/images/introduction/10-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="https://waitmoon.com/images/introduction/10-light.png">
  <img alt="Ice 规则树结构" src="https://waitmoon.com/images/introduction/10-light.png" width="520">
</picture>

### 关系节点（控制流）

| 类型 | 行为 | 短路 |
|------|------|:---:|
| **AND** | 所有子节点必须返回 true | 是 |
| **ANY** | 至少一个子节点返回 true | 是 |
| **ALL** | 所有子节点必须返回 true | 否 |
| **NONE** | 没有子节点返回 true | 否 |
| **TRUE** | 始终返回 true（执行所有子节点以获取副作用） | 否 |

每种类型都有对应的**并行**版本（`P_AND`、`P_ANY`、`P_ALL`、`P_NONE`、`P_TRUE`），可并发执行子节点。

### 叶子节点（业务逻辑）

| 类型 | 返回值 | 用途 |
|------|--------|------|
| **Flow** | `boolean` | 条件判断 —— "用户是否符合条件？" |
| **Result** | `boolean` | 业务操作 —— "发放优惠券" |
| **None** | `void` | 辅助操作 —— 日志记录、埋点、通知 |

### Roam（数据上下文）

**Roam** 是贯穿整棵规则树的线程安全数据容器。节点从 Roam 读取输入、向 Roam 写入输出——节点之间从不直接引用。这是实现节点隔离的关键设计。

```
Roam
├── put("uid", 12345)              // 扁平键值对
├── putDeep("user.level", "gold")  // 多层级键
├── get("uid")                     // → 12345
└── getDeep("user.level")          // → "gold"
```

- **线程安全**：Java `ConcurrentHashMap` / Go `sync.RWMutex` / Python `threading.RLock`
- **深层键**：`putDeep("a.b.c", value)` 自动创建嵌套结构
- **动态引用**：值前缀 `@` 表示运行时从 Roam 取值（如 `@uid` 读取 `roam.get("uid")`）

## 架构

```
┌──────────────────────────────────────────────────────────────┐
│                   共享存储 (ice-data/)                        │
│     ┌────────┐    ┌────────┐    ┌────────┐    ┌──────────┐  │
│     │ apps/  │    │ bases/ │    │ confs/ │    │ versions/│  │
│     └────────┘    └────────┘    └────────┘    └──────────┘  │
└──────────────────────────────────────────────────────────────┘
          ▲                                       ▲
          │ 写入                                  │ 读取（轮询）
          │                                       │
┌─────────┴─────────┐                 ┌───────────┴───────────┐
│    Ice Server      │                │      Ice Client       │
│                    │                │                       │
│  • Web UI          │                │  • 版本轮询           │
│  • 树形编辑器       │                │  • 热更新             │
│  • 应用 & 发布      │                │  • 内存执行           │
│  • Mock 调试        │                │  • 容错机制           │
└────────────────────┘                └───────────────────────┘
```

**关键设计：**

- **Server 与 Client 完全解耦** — Server 宕机不影响 Client，Client 从内存缓存继续执行规则
- **基于文件通信** — Server 和 Client 之间没有网络协议，仅通过共享目录（`ice-data/`）交互
- **增量更新** — Client 轮询 `versions/` 目录检测变化，仅应用增量部分；增量文件缺失时自动回退到全量加载

### 部署方式

| 模式 | 说明 |
|------|------|
| **单机部署** | Server + Client 同机，`ice-data/` 为本地目录 |
| **Docker Compose** | 容器化部署，通过 volume 挂载共享存储 |
| **分布式部署** | 多个 Server/Client 通过 NFS、AWS EFS 或 GCP Filestore 共享存储 |

## 快速开始

### 1. 部署 Server

```bash
docker run -d --name ice-server -p 8121:8121 \
  -v ./ice-data:/app/ice-data waitmoon/ice-server:latest
```

打开 `http://localhost:8121` 访问 Web UI。

### 2. 安装 Client SDK

<details open>
<summary><b>Java</b></summary>

```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-core</artifactId>
  <version>4.0.9</version>
</dependency>
```

</details>

<details>
<summary><b>Go</b></summary>

```bash
go get github.com/zjn-zjn/ice/sdks/go
```

</details>

<details>
<summary><b>Python</b></summary>

```bash
pip install ice-rules
```

</details>

### 3. 定义叶子节点

叶子节点是业务逻辑的载体。以下是一个简单的 `ScoreFlow`，判断某个值是否达到阈值：

<details open>
<summary><b>Java</b></summary>

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class ScoreFlow extends BaseLeafFlow {

    private double score;
    private String key;

    @Override
    protected boolean doFlow(IceRoam roam) {
        Number value = roam.getDeep(key);
        if (value == null) {
            return false;
        }
        return value.doubleValue() >= score;
    }
}
```

</details>

<details>
<summary><b>Go</b></summary>

```go
type ScoreFlow struct {
    Score float64 `json:"score" ice:"name:分数阈值,desc:判断分数的阈值"`
    Key   string  `json:"key" ice:"name:取值键,desc:从Roam中取值的键名"`
}

func (s *ScoreFlow) DoFlow(ctx context.Context, roam *icecontext.Roam) bool {
    value := roam.ValueDeep(s.Key).Float64Or(0)
    return value >= s.Score
}
```

注册节点：

```go
ice.RegisterLeaf("com.example.ScoreFlow",
    &ice.LeafMeta{Name: "分数判断", Desc: "判断分数是否达到阈值"},
    func() any { return &ScoreFlow{} })
```

</details>

<details>
<summary><b>Python</b></summary>

```python
@ice.leaf("com.example.ScoreFlow", name="分数判断", desc="判断分数是否达到阈值")
class ScoreFlow:
    score: Annotated[float, IceField(name="分数阈值")] = 0.0
    key: Annotated[str, IceField(name="取值键")] = ""

    def do_flow(self, roam: Roam) -> bool:
        value = roam.get_deep(self.key)
        if value is None:
            return False
        return float(value) >= self.score
```

</details>

### 4. 初始化 Client 并执行

<details open>
<summary><b>Java</b></summary>

```java
// 初始化
IceFileClient client = new IceFileClient(1, "./ice-data", "com.your.package");
client.start();

// 执行
IceRoam roam = IceRoam.create();
roam.setId(1L);
roam.put("uid", 12345);
roam.put("score", 95.5);
Ice.syncProcess(roam);
```

</details>

<details>
<summary><b>Go</b></summary>

```go
// 初始化
client, _ := ice.NewClient(1, "./ice-data")
client.Start()
defer client.Destroy()

// 执行
roam := ice.NewRoam()
roam.SetId(1)
roam.Put("uid", 12345)
roam.Put("score", 95.5)
ice.SyncProcess(context.Background(), roam)
```

</details>

<details>
<summary><b>Python</b></summary>

```python
# 初始化
client = ice.FileClient(app=1, storage_path="./ice-data")
client.start()

# 执行
roam = ice.Roam.create()
roam.set_id(1)
roam.put("uid", 12345)
roam.put("score", 95.5)
ice.sync_process(roam)
```

</details>

### 5. 在 Web UI 中配置规则

打开 Web UI → 创建规则树 → 编排节点 → 点击**应用**。配置变更在数秒内生效，无需重启应用。

## 与传统规则引擎的对比

| | Ice | 传统规则引擎（如 Drools） |
|---|---|---|
| **学习成本** | 5 分钟上手，可视化配置，无需 DSL | 需要学习规则语言语法 |
| **部署方式** | Docker 一键部署，零外部依赖 | 需要数据库 + 中间件 |
| **配置方式** | Web UI 树形编辑器 | 文本/代码编写规则 |
| **执行性能** | 纯内存执行，亚毫秒级延迟 | 编译和解释开销 |
| **热更新** | 秒级自动生效，无需重启 | 通常需要重启应用 |
| **变更影响** | 节点隔离，修改只影响当前节点 | 规则链级联影响 |
| **运维复杂度** | 单二进制 + 文件目录 | 多组件基础设施 |

## 应用场景

| 场景 | 示例 |
|------|------|
| **营销活动** | 优惠券、折扣、促销活动、拼团、秒杀 |
| **风控** | 信用评估、反欺诈检测、实时风险评估 |
| **动态定价** | 价格策略、折扣规则、阶梯定价、动态调价 |
| **权限控制** | 权限管理、功能开关、基于角色的访问控制 |
| **流程编排** | 审批流程、订单处理、工单路由、状态机 |

## 配置参考

### Client 配置

| 参数 | 必填 | 默认值 | 说明 |
|------|:---:|--------|------|
| `app` | 是 | — | 应用 ID |
| `storagePath` | 是 | — | 共享 `ice-data/` 目录路径 |
| `scan` | 仅 Java | — | 节点自动扫描的包路径 |
| `pollInterval` | 否 | `2s` | 版本变化检查频率 |
| `heartbeatInterval` | 否 | `10s` | 心跳上报间隔 |
| `parallelism` | 否 | ForkJoinPool | 并行节点的线程池大小 |
| `lane` | 否 | — | Lane 名称，用于流量隔离/分支测试 |

### Server 配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `port` | `8121` | 服务端口 |
| `storage-path` | `./ice-data` | 文件存储目录 |
| `mode` | `open` | `open`（正常模式）或 `controlled`（只读模式） |
| `client-timeout` | `30s` | Client 不活跃超时时间 |
| `version-retention` | `1000` | 保留的版本文件数量 |
| `publish-targets` | — | 远程 Server 地址，用于多实例发布 |

## 文档

**指南**
- [快速开始](https://waitmoon.com/guide/getting-started.html) — 部署、集成并运行第一条规则
- [核心概念](https://waitmoon.com/guide/concepts.html) — 规则树、节点、Roam 与执行模型
- [架构设计](https://waitmoon.com/guide/architecture.html) — 设计决策与部署模式
- [常见问题](https://waitmoon.com/guide/faq.html) — 常见问题与排查指南

**参考**
- [节点类型](https://waitmoon.com/reference/node-types.html) — 所有关系节点和叶子节点详解
- [Client 配置](https://waitmoon.com/reference/client-config.html) — 完整 Client 参数参考
- [Server 配置](https://waitmoon.com/reference/server-config.html) — 完整 Server 参数参考
- [Roam API](https://waitmoon.com/reference/roam-api.html) — 各 SDK 的数据上下文 API

**SDK 指南**
- [Java SDK](https://waitmoon.com/sdk/java.html)
- [Go SDK](https://waitmoon.com/sdk/go.html)
- [Python SDK](https://waitmoon.com/sdk/python.html)

## 社区

- [GitHub Issues](https://github.com/zjn-zjn/ice/issues) — Bug 反馈与功能建议
- [GitHub Discussions](https://github.com/zjn-zjn/ice/discussions) — 问题讨论
- [交流群](https://waitmoon.com/community/community.html) — 微信交流群
- [在线体验](https://eg.waitmoon.com) — 无需安装，直接体验 Ice

## 参与贡献

欢迎任何形式的贡献！无论是 Bug 反馈、功能建议、文档改进还是代码贡献——随时欢迎提 Issue 或提交 Pull Request。

## 许可证

[Apache License 2.0](LICENSE)
