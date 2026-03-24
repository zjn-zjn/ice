# Ice Go SDK

Ice 规则引擎的 Go 实现，与 Java ice-core 功能一致。

## 安装

```bash
go get github.com/zjn-zjn/ice/sdks/go
```

## 快速开始

### 1. 注册叶子节点

```go
package main

import (
    "github.com/zjn-zjn/ice/sdks/go"
    "github.com/zjn-zjn/ice/sdks/go/context"
)

// ScoreCheck 是一个 Flow 类型的叶子节点
type ScoreCheck struct {
    Threshold int `json:"threshold"`
}

// DoFlow 实现 LeafFlow 接口
func (s *ScoreCheck) DoFlow(roam *context.Roam) bool {
    return roam.GetInt("score", 0) >= s.Threshold
}

func init() {
    // 注册叶子节点
    ice.RegisterLeaf("com.example.ScoreCheck", nil, func() any {
        return &ScoreCheck{}
    })
}
```

### 2. 启动客户端

```go
func main() {
    // 创建文件客户端（最简方式）
    client, err := ice.NewClient(1, "./ice-data")
    if err != nil {
        panic(err)
    }
    
    // 启动客户端
    if err := client.Start(); err != nil {
        panic(err)
    }
    defer client.Destroy()
    
    // 等待启动完成
    client.WaitStarted()
    
    // 执行规则
    roam := ice.NewRoam()
    roam.SetId(1)
    roam.Put("score", 85)

    results := ice.SyncProcess(context.Background(), roam)
    // 处理结果...
}
```

## 叶子节点类型

Go SDK 支持 3 种叶子节点接口，自动检测类型：

### Flow 类型（返回 TRUE/FALSE）
- `LeafFlow` - `DoFlow(roam *Roam) bool`

### Result 类型（返回 TRUE/FALSE）
- `LeafResult` - `DoResult(roam *Roam) bool`

### None 类型（返回 NONE）
- `LeafNone` - `DoNone(roam *Roam)`

## 关系节点

### 串行关系节点
- `And` - 遇到 FALSE 立即返回 FALSE
- `Any` - 遇到 TRUE 立即返回 TRUE
- `All` - 执行所有子节点
- `True` - 执行所有子节点，返回 TRUE
- `None` - 执行所有子节点，返回 NONE

### 并行关系节点
- `ParallelAnd`, `ParallelAny`, `ParallelAll`, `ParallelTrue`, `ParallelNone`
- 使用 goroutine pool (ants) 并行执行子节点

## 配置

### 自定义日志

ice 使用 Go 标准库 `log/slog` 作为日志门面，可直接传入 `*slog.Logger`：

```go
import "log/slog"

// 使用 JSON 格式输出
ice.SetLogger(slog.New(slog.NewJSONHandler(os.Stdout, nil)))

// 或接入第三方日志库（zap、zerolog 等均提供 slog.Handler 适配）
ice.SetLogger(slog.New(zapSlogHandler))
```

### 初始化执行器池

```go
// 设置 goroutine pool 大小
ice.InitExecutor(100)
```

## 数据结构

### Roam

线程安全的 map，支持：
- `Put(key, value)` - 存储值
- `Get(key)` - 获取值
- `GetDeep("a.b.c")` - 多级 key 访问
- `Resolve("@key")` - 引用其他 key 的值

### _ice 元信息

执行元信息（存储在 Roam 的 `_ice` key 中，是一个普通 map）：
- `GetId()` / `SetId()` - Handler ID
- `GetScene()` / `SetScene()` - 场景名
- `GetNid()` / `SetNid()` - 配置节点 ID
- `GetTs()` / `SetTs()` - 请求时间戳
- `GetDebug()` / `SetDebug()` - 调试标志
- `GetTrace()` / `SetTrace()` - 追踪 ID

## 与 Java 兼容性

- JSON 序列化格式与 Java 一致
- 可共享 `ice-data` 目录
- 节点类型和关系语义一致

## 版本要求

- Go 1.21+

## License

Apache-2.0

