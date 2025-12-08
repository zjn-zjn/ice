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

// DoRoamFlow 实现 LeafRoamFlow 接口
func (s *ScoreCheck) DoRoamFlow(roam *context.Roam) bool {
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
    pack := ice.NewPack().
        SetIceId(1).
        SetRoam(ice.NewRoam().Put("score", 85))
    
    results := ice.SyncProcess(pack)
    // 处理结果...
}
```

## 叶子节点类型

Go SDK 支持 9 种叶子节点接口，自动检测类型：

### Flow 类型（返回 TRUE/FALSE）
- `LeafRoamFlow` - `DoRoamFlow(roam *Roam) bool`
- `LeafPackFlow` - `DoPackFlow(pack *Pack) bool`
- `LeafFlow` - `DoFlow(ctx *Context) bool`

### Result 类型（返回 TRUE/FALSE）
- `LeafRoamResult` - `DoRoamResult(roam *Roam) bool`
- `LeafPackResult` - `DoPackResult(pack *Pack) bool`
- `LeafResult` - `DoResult(ctx *Context) bool`

### None 类型（返回 NONE）
- `LeafRoamNone` - `DoRoamNone(roam *Roam)`
- `LeafPackNone` - `DoPackNone(pack *Pack)`
- `LeafNone` - `DoNone(ctx *Context)`

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

```go
import "github.com/zjn-zjn/ice/sdks/go/log"

// 实现 Logger 接口
type MyLogger struct{}

func (l *MyLogger) Debug(msg string, args ...any) { /* ... */ }
func (l *MyLogger) Info(msg string, args ...any)  { /* ... */ }
func (l *MyLogger) Warn(msg string, args ...any)  { /* ... */ }
func (l *MyLogger) Error(msg string, args ...any) { /* ... */ }

// 设置日志
ice.SetLogger(&MyLogger{})
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
- `GetMulti("a.b.c")` - 多级 key 访问
- `GetUnion("@key")` - 引用其他 key 的值

### Pack

执行输入：
- `IceId` - Handler ID
- `Scene` - 场景名
- `ConfId` - 配置节点 ID
- `Roam` - 业务数据
- `Debug` - 调试标志

## 与 Java 兼容性

- JSON 序列化格式与 Java 一致
- 可共享 `ice-data` 目录
- 节点类型和关系语义一致

## 版本要求

- Go 1.21+

## License

Apache-2.0

