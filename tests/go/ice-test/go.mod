module github.com/zjn-zjn/ice/tests/go/ice-test

go 1.21.0

toolchain go1.24.4

require (
	github.com/zjn-zjn/ice/sdks/go v0.0.0
	gopkg.in/yaml.v3 v3.0.1
)

require (
	github.com/panjf2000/ants/v2 v2.11.3 // indirect
	github.com/spf13/cast v1.10.0 // indirect
	golang.org/x/sync v0.11.0 // indirect
)

replace github.com/zjn-zjn/ice/sdks/go => ../../../sdks/go
