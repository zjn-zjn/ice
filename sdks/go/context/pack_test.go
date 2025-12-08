package context

import (
	"testing"

	"github.com/zjn-zjn/ice/sdks/go/enum"
)

func TestPack_NewPack(t *testing.T) {
	pack := NewPack()

	if pack.Roam == nil {
		t.Error("Roam should not be nil")
	}
	if pack.Type != enum.RequestFormal {
		t.Error("Type should be RequestFormal")
	}
	if pack.RequestTime <= 0 {
		t.Error("RequestTime should be set")
	}
	if pack.TraceId == "" {
		t.Error("TraceId should be generated")
	}
}

func TestPack_NewPackWithTraceId(t *testing.T) {
	traceId := "custom-trace-id"
	pack := NewPackWithTraceId(traceId)

	if pack.TraceId != traceId {
		t.Errorf("expected traceId=%s, got %s", traceId, pack.TraceId)
	}

	// Empty trace id should generate one
	pack2 := NewPackWithTraceId("")
	if pack2.TraceId == "" {
		t.Error("empty traceId should be replaced with generated one")
	}
}

func TestPack_Chaining(t *testing.T) {
	pack := NewPack().
		SetIceId(100).
		SetScene("test-scene").
		SetConfId(200).
		SetDebug(1).
		SetPriority(5)

	if pack.IceId != 100 {
		t.Error("expected IceId=100")
	}
	if pack.Scene != "test-scene" {
		t.Error("expected Scene=test-scene")
	}
	if pack.ConfId != 200 {
		t.Error("expected ConfId=200")
	}
	if pack.Debug != 1 {
		t.Error("expected Debug=1")
	}
	if pack.Priority != 5 {
		t.Error("expected Priority=5")
	}
}

func TestPack_SetRoam(t *testing.T) {
	roam := NewRoam()
	roam.Put("key", "value")

	pack := NewPack().SetRoam(roam)

	if pack.Roam.GetString("key") != "value" {
		t.Error("expected roam to be set correctly")
	}
}

func TestPack_Clone(t *testing.T) {
	pack := NewPack().
		SetIceId(100).
		SetScene("scene").
		SetConfId(200).
		SetDebug(3).
		SetPriority(10)
	pack.Roam.Put("original", "value")

	cloned := pack.Clone()

	// Verify cloned values
	if cloned.IceId != 100 {
		t.Error("cloned IceId mismatch")
	}
	if cloned.Scene != "scene" {
		t.Error("cloned Scene mismatch")
	}
	if cloned.ConfId != 200 {
		t.Error("cloned ConfId mismatch")
	}
	if cloned.Debug != 3 {
		t.Error("cloned Debug mismatch")
	}
	if cloned.Priority != 10 {
		t.Error("cloned Priority mismatch")
	}
	if cloned.TraceId != pack.TraceId {
		t.Error("cloned TraceId should be same")
	}
	if cloned.RequestTime != pack.RequestTime {
		t.Error("cloned RequestTime should be same")
	}

	// Verify roam is shallow copied
	if cloned.Roam.GetString("original") != "value" {
		t.Error("cloned roam should have original value")
	}

	// Modify cloned should not affect original
	cloned.IceId = 999
	cloned.Roam.Put("original", "modified")
	if pack.IceId != 100 {
		t.Error("original IceId should not be modified")
	}
	if pack.Roam.GetString("original") != "value" {
		t.Error("original roam should not be modified")
	}
}


