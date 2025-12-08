# Ice Python SDK

Ice 规则引擎的 Python 实现，与 Java ice-core 和 Go SDK 功能一致。

## 安装

```bash
pip install ice-rules
```

## 快速开始

### 1. 注册叶子节点

```python
import ice
from ice import Roam

@ice.leaf("com.example.ScoreFlow")
class ScoreFlow:
    threshold: int = 0
    
    def do_roam_flow(self, roam: Roam) -> bool:
        return roam.get_int("score", 0) >= self.threshold
```

### 2. 启动客户端

```python
# 同步方式
client = ice.FileClient(app=1, storage_path="./ice-data")
client.start()

pack = ice.Pack(ice_id=1)
pack.roam.put("score", 85)
results = ice.sync_process(pack)

client.destroy()
```

### 3. 异步方式

```python
import asyncio

async def main():
    client = ice.AsyncFileClient(app=1, storage_path="./ice-data")
    await client.start()
    
    pack = ice.Pack(ice_id=1)
    pack.roam.put("score", 85)
    results = await ice.async_process(pack)
    
    await client.destroy()

asyncio.run(main())
```

## 叶子节点类型

### Flow 类型（返回 True/False）
- `do_flow(ctx: Context) -> bool`
- `do_pack_flow(pack: Pack) -> bool`
- `do_roam_flow(roam: Roam) -> bool`

### Result 类型（返回 True/False）
- `do_result(ctx: Context) -> bool`
- `do_pack_result(pack: Pack) -> bool`
- `do_roam_result(roam: Roam) -> bool`

### None 类型（无返回值）
- `do_none(ctx: Context) -> None`
- `do_pack_none(pack: Pack) -> None`
- `do_roam_none(roam: Roam) -> None`

## 版本要求

- Python >= 3.11

## License

Apache-2.0

