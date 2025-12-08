# Ice Python Test Client

Python 测试客户端，用于测试 Ice Python SDK。

## 结构

```
ice-test/
├── main.py           # 主程序入口
├── config.yml        # 配置文件
├── flow/             # Flow 类型叶子节点
│   └── score_flow.py
├── result/           # Result 类型叶子节点
│   ├── amount_result.py
│   └── point_result.py
├── none/             # None 类型叶子节点
│   └── none_nodes.py
├── service/          # 模拟服务
│   └── send_service.py
└── requirements.txt  # 依赖
```

## 配置

编辑 `config.yml` 配置文件：

```yaml
server:
  port: 8085

ice:
  app: 1
  storage:
    path: ./ice-data
  poll-interval: 5
  heartbeat-interval: 10
  pool:
    parallelism: -1

environment: dev
```

## 运行

```bash
cd tests/python/ice-test

# 创建虚拟环境 (推荐)
python3 -m venv .venv
source .venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 运行 (使用 config.yml)
python main.py

# 或者指定配置文件
python main.py -c /path/to/config.yml

# 命令行参数会覆盖配置文件
python main.py --port 9000 --app 2
```

## API

- `POST /test` - 执行规则测试
- `GET /recharge?cost=xxx&uid=xxx` - 充值场景测试
- `GET /consume?cost=xxx&uid=xxx` - 消费场景测试
- `GET /health` - 健康检查

