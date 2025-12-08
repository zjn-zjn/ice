<p align="center">
  <img width="140" alt="Ice" src="https://waitmoon.com/images/hero.svg">
</p>

<h1 align="center">Ice</h1>

<p align="center">
  轻量级可视化规则引擎，致力于解决灵活繁复的硬编码问题
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0.html"><img src="https://img.shields.io/badge/license-Apache%202-blue.svg" alt="License"></a>
  <a href="https://central.sonatype.com/artifact/com.waitmoon.ice/ice"><img src="https://img.shields.io/maven-central/v/com.waitmoon.ice/ice.svg" alt="Maven Central"></a>
  <a href="https://pkg.go.dev/github.com/zjn-zjn/ice/sdks/go"><img src="https://img.shields.io/badge/go-reference-blue.svg" alt="Go Reference"></a>
  <a href="https://pypi.org/project/ice-rules/"><img src="https://img.shields.io/pypi/v/ice-rules.svg" alt="PyPI"></a>
  <a href="https://hub.docker.com/r/waitmoon/ice-server"><img src="https://img.shields.io/docker/pulls/waitmoon/ice-server" alt="Docker Pulls"></a>
</p>

<p align="center">
  <a href="https://waitmoon.com/">文档</a> · 
  <a href="https://eg.waitmoon.com">在线体验</a> · 
  <a href="https://waitmoon.com/guide/getting-started.html">快速开始</a>
</p>

<p align="center">
  <a href="./README.md">English</a>
</p>

---

## 特性

- **可视化配置** — Web 树形编辑器进行规则编排
- **零依赖** — 无需数据库或中间件，基于文件存储
- **多语言 SDK** — Java、Go、Python 功能完全对等
- **热更新** — 配置变更秒级生效
- **高性能** — 纯内存执行，毫秒级响应

## 为什么选择 Ice？

| | Ice | 传统规则引擎 |
|---|---|---|
| **学习成本** | 5 分钟上手 | 需要学习 DSL 语法 |
| **部署方式** | Docker 一键部署 | 需要数据库/中间件 |
| **配置方式** | 可视化 Web 界面 | 文本/代码配置 |
| **执行性能** | 纯内存，毫秒级 | 存在编译开销 |
| **热更新** | 秒级生效，无需重启 | 通常需要重启 |

## 使用场景

| 场景 | 说明 |
|------|------|
| **营销活动** | 优惠券、折扣、促销、秒杀规则 |
| **风控系统** | 信用评估、反欺诈、实时决策 |
| **动态定价** | 价格策略、折扣规则、阶梯定价 |
| **权限控制** | 权限管理、功能开关、A/B 测试 |
| **流程编排** | 审批流程、订单处理、状态机 |

## 安装

### Server

```bash
docker run -d --name ice-server -p 8121:8121 \
  -v ./ice-data:/app/ice-data waitmoon/ice-server:latest
```

### Client SDK

<details open>
<summary><b>Java</b> · <a href="https://waitmoon.com/guide/getting-started.html">文档</a></summary>

**Spring Boot 3.x**

```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-spring-boot-starter-3x</artifactId>
  <version>2.0.1</version>
</dependency>
```

**Spring Boot 2.x**

```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-spring-boot-starter-2x</artifactId>
  <version>2.0.1</version>
</dependency>
```

**非 Spring 项目**

```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-core</artifactId>
  <version>2.0.1</version>
</dependency>
```

配置：

```yaml
ice:
  app: 1
  storage:
    path: ./ice-data
  scan: com.your.package
```

使用：

```java
IcePack pack = new IcePack();
pack.setIceId(1L);
pack.setRoam(new IceRoam().put("uid", 12345));
Ice.syncProcess(pack);
```

</details>

<details>
<summary><b>Go</b> · <a href="https://waitmoon.com/guide/go-sdk.html">文档</a></summary>

```bash
go get github.com/zjn-zjn/ice/sdks/go
```

使用：

```go
import ice "github.com/zjn-zjn/ice/sdks/go"

func main() {
    client, _ := ice.NewClient(1, "./ice-data")
    client.Start()
    defer client.Destroy()

    pack := ice.NewPack().SetIceId(1)
    pack.Roam.Put("uid", 12345)
    ice.SyncProcess(context.Background(), pack)
}
```

</details>

<details>
<summary><b>Python</b> · <a href="https://waitmoon.com/guide/python-sdk.html">文档</a></summary>

```bash
pip install ice-rules
```

使用：

```python
import ice

client = ice.FileClient(app=1, storage_path="./ice-data")
client.start()

pack = ice.Pack(ice_id=1)
pack.roam.put("uid", 12345)
ice.sync_process(pack)
```

</details>

## 架构

```
┌──────────────────────────────────────────────────────────┐
│                    共享存储 (ice-data/)                   │
│    ┌────────┐   ┌────────┐   ┌────────┐   ┌──────────┐   │
│    │ apps/  │   │ bases/ │   │ confs/ │   │ versions/│   │
│    └────────┘   └────────┘   └────────┘   └──────────┘   │
└──────────────────────────────────────────────────────────┘
         ▲                                    ▲
         │ 写入                                │ 读取 (轮询)
         │                                    │
┌────────┴────────┐                ┌──────────┴──────────┐
│   Ice Server    │                │     Ice Client      │
│                 │                │                     │
│  • Web 界面      │                │  • 版本轮询          │
│  • 规则编辑      │                │  • 热更新            │
│  • 配置发布      │                │  • 内存执行           │
└─────────────────┘                └─────────────────────┘
```

## 文档

- [快速开始](https://waitmoon.com/guide/getting-started.html)
- [架构设计](https://waitmoon.com/advanced/architecture.html)
- [配置说明](https://waitmoon.com/guide/detail.html)
- [常见问题](https://waitmoon.com/guide/qa.html)
- [视频教程](https://www.bilibili.com/video/BV1Q34y1R7KF)

## 社区

- [GitHub Issues](https://github.com/zjn-zjn/ice/issues) — Bug 反馈和功能建议
- [GitHub Discussions](https://github.com/zjn-zjn/ice/discussions) — 问题讨论
- [交流群](https://waitmoon.com/community/community.html) — 微信交流群

## 许可证

[Apache License 2.0](LICENSE)
