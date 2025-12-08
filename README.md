<p align="center">
  <img width="140" alt="Ice" src="https://waitmoon.com/images/hero.svg">
</p>

<h1 align="center">Ice</h1>

<p align="center">
  A lightweight visual rule engine for flexible business orchestration
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0.html"><img src="https://img.shields.io/badge/license-Apache%202-blue.svg" alt="License"></a>
  <a href="https://central.sonatype.com/artifact/com.waitmoon.ice/ice"><img src="https://img.shields.io/maven-central/v/com.waitmoon.ice/ice.svg" alt="Maven Central"></a>
  <a href="https://github.com/zjn-zjn/ice/tree/main/sdks/go"><img src="https://img.shields.io/badge/go-reference-blue.svg" alt="Go Reference"></a>
  <a href="https://pypi.org/project/ice-rules/"><img src="https://img.shields.io/pypi/v/ice-rules.svg" alt="PyPI"></a>
  <a href="https://hub.docker.com/r/waitmoon/ice-server"><img src="https://img.shields.io/docker/pulls/waitmoon/ice-server" alt="Docker Pulls"></a>
</p>

<p align="center">
  <a href="https://waitmoon.com/en/">Documentation</a> · 
  <a href="https://eg.waitmoon.com">Live Demo</a> · 
  <a href="https://waitmoon.com/en/guide/getting-started.html">Getting Started</a>
</p>

<p align="center">
  <a href="./README_zh.md">中文</a>
</p>

---

## Features

- **Visual Configuration** — Web-based tree editor for rule orchestration
- **Zero Dependencies** — No database or middleware required, file-based storage
- **Multi-Language SDKs** — Java, Go, and Python with full feature parity
- **Hot Reload** — Configuration changes take effect in seconds
- **High Performance** — Pure in-memory execution with millisecond latency

## Installation

### Server

```bash
docker run -d --name ice-server -p 8121:8121 \
  -v ./ice-data:/app/ice-data waitmoon/ice-server:latest
```

### Client SDKs

<details open>
<summary><b>Java</b> · <a href="https://waitmoon.com/en/guide/getting-started.html">Documentation</a></summary>

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

**Non-Spring**

```xml
<dependency>
  <groupId>com.waitmoon.ice</groupId>
  <artifactId>ice-core</artifactId>
  <version>2.0.1</version>
</dependency>
```

Configuration:

```yaml
ice:
  app: 1
  storage:
    path: ./ice-data
  scan: com.your.package
```

Usage:

```java
IcePack pack = new IcePack();
pack.setIceId(1L);
pack.setRoam(new IceRoam().put("uid", 12345));
Ice.syncProcess(pack);
```

</details>

<details>
<summary><b>Go</b> · <a href="https://waitmoon.com/en/guide/go-sdk.html">Documentation</a></summary>

```bash
go get github.com/zjn-zjn/ice/sdks/go
```

Usage:

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
<summary><b>Python</b> · <a href="https://waitmoon.com/en/guide/python-sdk.html">Documentation</a></summary>

```bash
pip install ice-rules
```

Usage:

```python
import ice

client = ice.FileClient(app=1, storage_path="./ice-data")
client.start()

pack = ice.Pack(ice_id=1)
pack.roam.put("uid", 12345)
ice.sync_process(pack)
```

</details>

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  Shared Storage (ice-data/)              │
│    ┌────────┐   ┌────────┐   ┌────────┐   ┌──────────┐   │
│    │ apps/  │   │ bases/ │   │ confs/ │   │ versions/│   │
│    └────────┘   └────────┘   └────────┘   └──────────┘   │
└──────────────────────────────────────────────────────────┘
         ▲                                    ▲
         │ Write                              │ Read (Poll)
         │                                    │
┌────────┴────────┐                ┌──────────┴──────────┐
│   Ice Server    │                │     Ice Client      │
│                 │                │                     │
│  • Web UI       │                │  • Version polling  │
│  • Rule editor  │                │  • Hot reload       │
│  • Publishing   │                │  • In-memory exec   │
└─────────────────┘                └─────────────────────┘
```

## Documentation

- [Getting Started](https://waitmoon.com/en/guide/getting-started.html)
- [Architecture Design](https://waitmoon.com/en/advanced/architecture.html)
- [Configuration Guide](https://waitmoon.com/en/guide/detail.html)
- [FAQ](https://waitmoon.com/en/guide/qa.html)

## License

[Apache License 2.0](LICENSE)
