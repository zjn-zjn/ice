<p align="center">
  <img width="128" alt="Ice" src="https://user-images.githubusercontent.com/33447125/151098049-72aaf8d1-b759-4d84-bf6b-1a2260033582.png">
</p>

<h1 align="center">Ice</h1>

<p align="center">
  A lightweight visual rule engine for flexible business orchestration
</p>

<p align="center">
  <a href="https://www.apache.org/licenses/LICENSE-2.0.html"><img src="https://img.shields.io/badge/license-Apache%202-blue.svg" alt="License"></a>
  <a href="https://central.sonatype.com/artifact/com.waitmoon.ice/ice"><img src="https://img.shields.io/maven-central/v/com.waitmoon.ice/ice.svg" alt="Maven Central"></a>
  <a href="https://pkg.go.dev/github.com/waitmoon/ice/sdks/go"><img src="https://pkg.go.dev/badge/github.com/waitmoon/ice/sdks/go.svg" alt="Go Reference"></a>
  <a href="https://pypi.org/project/ice-rules/"><img src="https://img.shields.io/pypi/v/ice-rules.svg" alt="PyPI"></a>
</p>

<p align="center">
  <a href="./README_zh.md">中文文档</a> · <a href="https://waitmoon.com/en/">Documentation</a> · <a href="https://eg.waitmoon.com">Live Demo</a>
</p>

---

## Overview

Ice is a rule engine designed to solve complex hard-coded business logic problems. It provides a web-based visual interface for rule configuration and supports hot-reload without application restart.

**Key Features**

- Visual tree-based rule orchestration with web UI
- Zero external dependencies (no database or middleware required)
- Multi-language SDKs: Java, Go, Python
- Hot configuration reload in seconds
- Pure in-memory execution with millisecond latency

## Quick Start

### Deploy Server

```bash
docker run -d --name ice-server -p 8121:8121 \
  -v ./ice-data:/app/ice-data waitmoon/ice-server:latest
```

Visit http://localhost:8121 to access the configuration interface.

### Install SDK

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

### Configuration

```yaml
ice:
  app: 1
  storage:
    path: ./ice-data
```

> The storage path must be shared between Server and Client.

### Execute Rules

```java
IcePack pack = new IcePack();
pack.setIceId(1L);
pack.setRoam(new IceRoam().put("uid", 12345));
Ice.syncProcess(pack);
```

## Documentation

- [Getting Started](https://waitmoon.com/en/guide/getting-started.html)
- [Architecture](https://waitmoon.com/en/advanced/architecture.html)
- [Go SDK Guide](https://waitmoon.com/en/guide/go-sdk.html)
- [Python SDK Guide](https://waitmoon.com/en/guide/python-sdk.html)

## License

[Apache License 2.0](LICENSE)
