package main

import (
	"testing"

	"github.com/zjn-zjn/ice/sdks/go/leaf"
)

func TestAutoScanRoamKeysFromSeparateRegister(t *testing.T) {
	// registerLeafNodes() is called from register.go (separate file from leaf definitions)
	registerLeafNodes()

	nodes := leaf.GetLeafNodes()
	roamKeysMap := map[string]int{}
	for _, n := range nodes {
		roamKeysMap[n.Clazz] = len(n.RoamKeys)
	}

	// ScoreFlow reads roam.ValueDeep(s.Key) -> 1 roam key
	if roamKeysMap["com.ice.test.flow.ScoreFlow"] != 1 {
		t.Errorf("ScoreFlow: expected 1 roam key, got %d", roamKeysMap["com.ice.test.flow.ScoreFlow"])
	}

	// AmountResult reads roam.ValueDeep(a.Key) + writes roam.Put("SEND_AMOUNT", ..) -> 2 roam keys
	if roamKeysMap["com.ice.test.result.AmountResult"] != 2 {
		t.Errorf("AmountResult: expected 2 roam keys, got %d", roamKeysMap["com.ice.test.result.AmountResult"])
	}

	// PointResult reads roam.ValueDeep(p.Key) + writes roam.Put("SEND_POINT", ..) -> 2 roam keys
	if roamKeysMap["com.ice.test.result.PointResult"] != 2 {
		t.Errorf("PointResult: expected 2 roam keys, got %d", roamKeysMap["com.ice.test.result.PointResult"])
	}

	// InitConfigResult writes roam.Put(i.ConfigKey, ..) -> 1 roam key
	if roamKeysMap["com.ice.test.result.InitConfigResult"] != 1 {
		t.Errorf("InitConfigResult: expected 1 roam key, got %d", roamKeysMap["com.ice.test.result.InitConfigResult"])
	}

	// RoamProbeLogNone reads roam.GetDeep(r.Key) -> 1 roam key
	if roamKeysMap["com.ice.test.none.RoamProbeLogNone"] != 1 {
		t.Errorf("RoamProbeLogNone: expected 1 roam key, got %d", roamKeysMap["com.ice.test.none.RoamProbeLogNone"])
	}

	// TimeChangeNone accesses roam.GetMeta() only (no roam key access) -> 0
	if roamKeysMap["com.ice.test.none.TimeChangeNone"] != 0 {
		t.Errorf("TimeChangeNone: expected 0 roam keys, got %d", roamKeysMap["com.ice.test.none.TimeChangeNone"])
	}
}
