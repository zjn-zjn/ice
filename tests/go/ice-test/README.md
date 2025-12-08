# Ice Go Test Application

This is a test application demonstrating the Go SDK for Ice, equivalent to the Java `ice-test` application.

## Build

```bash
cd tests/go/ice-test
go build -o ice-test .
```

## Configuration

Edit `config.yml`:

```yaml
server:
  port: 8084

ice:
  app: 1
  storage:
    path: ./ice-data
  poll-interval: 5
  heartbeat-interval: 10
  pool:
    parallelism: -1
```

## Run

```bash
# Run with config.yml (default)
./ice-test

# Run with custom config file
./ice-test -config /path/to/config.yml

# Command line args override config file
./ice-test -port 8085 -app 2
```

## API Endpoints

### POST /test
Execute ice rules with a custom pack.

```bash
curl -X POST http://localhost:8084/test \
  -H "Content-Type: application/json" \
  -d '{"scene": "recharge", "roam": {"cost": 100, "uid": 123}}'
```

### GET /recharge
Test recharge scene.

```bash
curl "http://localhost:8084/recharge?cost=100&uid=123"
```

### GET /consume
Test consume scene.

```bash
curl "http://localhost:8084/consume?cost=50&uid=123"
```

### GET /health
Health check endpoint.

```bash
curl http://localhost:8084/health
```

## Registered Leaf Nodes

| Class Name | Type | Description |
|------------|------|-------------|
| `com.ice.test.flow.ScoreFlow` | Flow | Check if roam[key] >= score |
| `com.ice.test.flow.ScoreFlow2` | Flow | Another score check variant |
| `com.ice.test.result.AmountResult` | Result | Grant amount to user |
| `com.ice.test.result.AmountResult2` | Result | Another amount grant variant |
| `com.ice.test.result.PointResult` | Result | Grant points to user |
| `com.ice.test.result.PointResult2` | Result | Another point grant variant |
| `com.ice.test.result.InitConfigResult` | Result | Initialize config in roam |
| `com.ice.test.none.RoamProbeLogNone` | None | Log roam content for debugging |
| `com.ice.test.none.TimeChangeNone` | None | Modify request time for testing |

