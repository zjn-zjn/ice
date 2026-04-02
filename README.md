<p align="center">
  <img width="140" alt="Ice" src="https://waitmoon.com/images/hero.svg">
</p>

<h1 align="center">Ice</h1>

<p align="center">
  A lightweight, zero-dependency rule engine with visual tree-based orchestration
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
  <a href="https://waitmoon.com/en/">Documentation</a> &middot;
  <a href="https://eg.waitmoon.com">Live Demo</a> &middot;
  <a href="https://waitmoon.com/en/guide/getting-started.html">Getting Started</a> &middot;
  <a href="https://waitmoon.com/en/CHANGELOG.html">Changelog</a>
</p>

<p align="center">
  <a href="./README_zh.md">中文</a>
</p>

---

## What is Ice?

Ice is a rule engine that takes a fundamentally different approach: **rules are organized as trees, not chains or tables**. Each node in the tree handles its own logic independently — modifying one node never creates cascading effects on others. Nodes communicate exclusively through a shared, thread-safe data context called **Roam**, rather than referencing each other directly.

The result is a system where business rules can be visually configured, hot-reloaded in seconds, and executed in-memory with sub-millisecond latency — all without requiring any database or middleware.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://waitmoon.com/images/introduction/2-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="https://waitmoon.com/images/introduction/2-light.png">
  <img alt="Ice Rule Example" src="https://waitmoon.com/images/introduction/2-light.png" width="600">
</picture>

## Key Features

|  | Feature | Description |
|---|---|---|
| **🌲** | **Tree-Based Orchestration** | Rules organized as trees — nodes are independent, changes to one node never cascade to others |
| **🎨** | **Visual Web Editor** | Configure rules through an intuitive web UI with a tree editor, no DSL to learn |
| **📦** | **Zero Dependencies** | No database, no message queue, no service registry — just files on disk |
| **🌍** | **Multi-Language SDKs** | Java, Go, and Python with full feature parity |
| **⚡** | **Hot Reload** | Changes take effect in seconds via automatic version polling — no restart needed |
| **🚀** | **High Performance** | Pure in-memory execution with sub-millisecond latency, zero network overhead |
| **♻️** | **Node Reuse** | Same node can be shared across multiple rule trees |
| **⚙️** | **Parallel Execution** | Built-in parallel relation nodes for concurrent child execution |
| **🔍** | **Mock Debugging** | Trigger rule execution remotely from the Web UI for real-time debugging |
| **🔀** | **Lane / Traffic Isolation** | Branch-based rule isolation for A/B testing, canary releases, and gradual rollouts |

## How It Works

### Rule Trees

Every rule in Ice is a **tree** composed of two types of nodes:

- **Relation Nodes** — control the execution flow (similar to logical operators)
- **Leaf Nodes** — contain your actual business logic

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://waitmoon.com/images/introduction/10-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="https://waitmoon.com/images/introduction/10-light.png">
  <img alt="Ice Rule Tree Structure" src="https://waitmoon.com/images/introduction/10-light.png" width="520">
</picture>

### Relation Nodes (Control Flow)

| Type | Behavior | Short-Circuit |
|------|----------|:---:|
| **AND** | All children must return true | Yes |
| **ANY** | At least one child returns true | Yes |
| **ALL** | All children must return true | No |
| **NONE** | No child returns true | No |
| **TRUE** | Always returns true (execute all children for side effects) | No |

Each type has a **parallel** variant (`P_AND`, `P_ANY`, `P_ALL`, `P_NONE`, `P_TRUE`) that executes children concurrently.

### Leaf Nodes (Business Logic)

| Type | Return | Purpose |
|------|--------|---------|
| **Flow** | `boolean` | Conditional checks — "Is the user eligible?" |
| **Result** | `boolean` | Business operations — "Issue the coupon" |
| **None** | `void` | Side effects — logging, metrics, notifications |

### Roam (Data Context)

**Roam** is the thread-safe data container that flows through the entire rule tree. Nodes read input from and write output to Roam — they never reference each other directly. This is the key to node isolation.

```
Roam
├── put("uid", 12345)              // flat key-value
├── putDeep("user.level", "gold")  // nested key
├── get("uid")                     // → 12345
└── getDeep("user.level")          // → "gold"
```

- **Thread-safe**: Java `ConcurrentHashMap` / Go `sync.RWMutex` / Python `threading.RLock`
- **Deep keys**: `putDeep("a.b.c", value)` auto-creates nested structures
- **Dynamic references**: prefix a value with `@` to resolve it from Roam at runtime (e.g., `@uid` reads `roam.get("uid")`)

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                   Shared Storage (ice-data/)                 │
│     ┌────────┐    ┌────────┐    ┌────────┐    ┌──────────┐  │
│     │ apps/  │    │ bases/ │    │ confs/ │    │ versions/│  │
│     └────────┘    └────────┘    └────────┘    └──────────┘  │
└──────────────────────────────────────────────────────────────┘
          ▲                                       ▲
          │ Write                                 │ Read (Poll)
          │                                       │
┌─────────┴─────────┐                 ┌───────────┴───────────┐
│    Ice Server      │                │      Ice Client       │
│                    │                │                       │
│  • Web UI          │                │  • Version polling    │
│  • Tree editor     │                │  • Hot reload         │
│  • Apply & publish │                │  • In-memory exec     │
│  • Mock debugging  │                │  • Fault tolerant     │
└────────────────────┘                └───────────────────────┘
```

**Key design decisions:**

- **Server and Client are fully decoupled** — if the Server goes down, Clients continue executing rules from their in-memory cache
- **File-based communication** — no network protocol between Server and Client, just a shared directory (`ice-data/`)
- **Incremental updates** — Client polls `versions/` for changes and applies only the delta; falls back to full reload if incremental files are missing

### Deployment Options

| Mode | Description |
|------|-------------|
| **Single Machine** | Server + Client on the same host, `ice-data/` is a local directory |
| **Docker Compose** | Containerized deployment with volume mounts |
| **Distributed** | Multiple Servers/Clients sharing storage via NFS, AWS EFS, or GCP Filestore |

## Quick Start

### 1. Deploy the Server

```bash
docker run -d --name ice-server -p 8121:8121 \
  -v ./ice-data:/app/ice-data waitmoon/ice-server:latest
```

Open `http://localhost:8121` to access the Web UI.

### 2. Install the Client SDK

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

### 3. Define Your Leaf Nodes

Leaf nodes are where your business logic lives. Here's a simple `ScoreFlow` that checks if a value meets a threshold:

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
    Score float64 `json:"score" ice:"name:Score Threshold,desc:Minimum score to pass"`
    Key   string  `json:"key" ice:"name:Roam Key,desc:Key to read from Roam"`
}

func (s *ScoreFlow) DoFlow(ctx context.Context, roam *icecontext.Roam) bool {
    value := roam.ValueDeep(s.Key).Float64Or(0)
    return value >= s.Score
}
```

Register it:

```go
ice.RegisterLeaf("com.example.ScoreFlow",
    &ice.LeafMeta{Name: "Score Check", Desc: "Check if score meets threshold"},
    func() any { return &ScoreFlow{} })
```

</details>

<details>
<summary><b>Python</b></summary>

```python
@ice.leaf("com.example.ScoreFlow", name="Score Check", desc="Check if score meets threshold")
class ScoreFlow:
    score: Annotated[float, IceField(name="Score Threshold")] = 0.0
    key: Annotated[str, IceField(name="Roam Key")] = ""

    def do_flow(self, roam: Roam) -> bool:
        value = roam.get_deep(self.key)
        if value is None:
            return False
        return float(value) >= self.score
```

</details>

### 4. Initialize the Client and Execute

<details open>
<summary><b>Java</b></summary>

```java
// Initialize
IceFileClient client = new IceFileClient(1, "./ice-data", "com.your.package");
client.start();

// Execute
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
// Initialize
client, _ := ice.NewClient(1, "./ice-data")
client.Start()
defer client.Destroy()

// Execute
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
# Initialize
client = ice.FileClient(app=1, storage_path="./ice-data")
client.start()

# Execute
roam = ice.Roam.create()
roam.set_id(1)
roam.put("uid", 12345)
roam.put("score", 95.5)
ice.sync_process(roam)
```

</details>

### 5. Configure Rules in the Web UI

Open the Web UI → create a rule tree → arrange your nodes → click **Apply**. Changes take effect within seconds — no restart required.

## Comparison with Traditional Rule Engines

| | Ice | Traditional (Drools, etc.) |
|---|---|---|
| **Learning Curve** | 5 minutes — visual configuration, no DSL | Requires learning rule language syntax |
| **Deployment** | Docker one-click, zero external dependencies | Requires database + middleware setup |
| **Configuration** | Web UI tree editor | Text/code-based rule files |
| **Performance** | In-memory, sub-millisecond latency | Compilation and interpretation overhead |
| **Hot Reload** | Seconds, automatic — no restart | Often requires application restart |
| **Change Impact** | Node-isolated — changes affect only that node | Cascading effects across rule chains |
| **Ops Complexity** | Single binary + file directory | Multi-component infrastructure |

## Use Cases

| Scenario | Examples |
|----------|---------|
| **Marketing Campaigns** | Coupons, discounts, promotions, group deals, flash sales |
| **Risk Control** | Credit assessment, anti-fraud detection, real-time risk evaluation |
| **Dynamic Pricing** | Price strategies, discount rules, tiered pricing, surge pricing |
| **Access Control** | Permission management, feature flags, role-based access |
| **Process Orchestration** | Approval workflows, order processing, ticket routing, state machines |

## Configuration Reference

### Client Configuration

| Parameter | Required | Default | Description |
|-----------|:---:|---------|-------------|
| `app` | Yes | — | Application ID |
| `storagePath` | Yes | — | Path to shared `ice-data/` directory |
| `scan` | Java only | — | Package path for automatic node scanning |
| `pollInterval` | No | `2s` | How often to check for version changes |
| `heartbeatInterval` | No | `10s` | Heartbeat reporting interval |
| `parallelism` | No | ForkJoinPool | Thread pool size for parallel nodes |
| `lane` | No | — | Lane name for traffic isolation / branch testing |

### Server Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `port` | `8121` | Server HTTP port |
| `storage-path` | `./ice-data` | File storage directory |
| `mode` | `open` | `open` (normal) or `controlled` (read-only UI) |
| `client-timeout` | `30s` | Client inactivity timeout |
| `version-retention` | `1000` | Number of version files to retain |
| `publish-targets` | — | Remote server addresses for multi-instance publishing |

## Documentation

**Guide**
- [Getting Started](https://waitmoon.com/en/guide/getting-started.html) — Deploy, integrate, and run your first rule
- [Core Concepts](https://waitmoon.com/en/guide/concepts.html) — Trees, nodes, Roam, and execution model
- [Architecture](https://waitmoon.com/en/guide/architecture.html) — Design decisions and deployment patterns
- [FAQ](https://waitmoon.com/en/guide/faq.html) — Common questions and troubleshooting

**Reference**
- [Node Types](https://waitmoon.com/en/reference/node-types.html) — All relation and leaf node types in detail
- [Client Configuration](https://waitmoon.com/en/reference/client-config.html) — Full client parameter reference
- [Server Configuration](https://waitmoon.com/en/reference/server-config.html) — Full server parameter reference
- [Roam API](https://waitmoon.com/en/reference/roam-api.html) — Data context API across all SDKs

**SDK Guides**
- [Java SDK](https://waitmoon.com/en/sdk/java.html)
- [Go SDK](https://waitmoon.com/en/sdk/go.html)
- [Python SDK](https://waitmoon.com/en/sdk/python.html)

## Community

- [GitHub Issues](https://github.com/zjn-zjn/ice/issues) — Bug reports and feature requests
- [GitHub Discussions](https://github.com/zjn-zjn/ice/discussions) — Questions and community discussions
- [Live Demo](https://eg.waitmoon.com) — Try Ice without installing anything

## Contributing

Contributions are welcome! Whether it's bug reports, feature requests, documentation improvements, or code contributions — feel free to open an issue or submit a pull request.

## License

[Apache License 2.0](LICENSE)
