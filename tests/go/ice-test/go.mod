module github.com/waitmoon/ice/tests/go/ice-test

go 1.21

require (
	github.com/waitmoon/ice/sdks/go v0.0.0
	gopkg.in/yaml.v3 v3.0.1
)

require (
	github.com/panjf2000/ants/v2 v2.11.3 // indirect
	golang.org/x/sync v0.11.0 // indirect
)

replace github.com/waitmoon/ice/sdks/go => ../../../sdks/go
