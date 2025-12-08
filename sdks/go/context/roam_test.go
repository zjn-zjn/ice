package context

import (
	"sync"
	"testing"
)

func TestRoam_PutAndGet(t *testing.T) {
	roam := NewRoam()

	// Test Put and Get
	roam.Put("name", "test")
	roam.Put("age", 25)
	roam.Put("score", 85.5)
	roam.Put("active", true)

	if roam.GetString("name") != "test" {
		t.Error("expected name=test")
	}
	if roam.GetInt("age", 0) != 25 {
		t.Error("expected age=25")
	}
	if roam.GetFloat64("score", 0) != 85.5 {
		t.Error("expected score=85.5")
	}
	if !roam.GetBool("active") {
		t.Error("expected active=true")
	}
}

func TestRoam_NilHandling(t *testing.T) {
	roam := NewRoam()

	// Empty key should not be stored
	roam.Put("", "value")
	if roam.Get("") != nil {
		t.Error("empty key should return nil")
	}

	// Nil value should not be stored
	roam.Put("key", nil)
	if roam.Get("key") != nil {
		t.Error("nil value should not be stored")
	}
}

func TestRoam_MultiKey(t *testing.T) {
	roam := NewRoam()

	// Test PutMulti
	roam.PutMulti("user.profile.age", 30)
	roam.PutMulti("user.profile.name", "Alice")
	roam.PutMulti("user.score", 100)

	// Test GetMulti
	if roam.GetMulti("user.profile.age") != 30 {
		t.Error("expected user.profile.age=30")
	}
	if roam.GetMulti("user.profile.name") != "Alice" {
		t.Error("expected user.profile.name=Alice")
	}
	if roam.GetMulti("user.score") != 100 {
		t.Error("expected user.score=100")
	}

	// Test non-existent path
	if roam.GetMulti("user.profile.notexist") != nil {
		t.Error("expected nil for non-existent path")
	}
	if roam.GetMulti("notexist.path") != nil {
		t.Error("expected nil for non-existent path")
	}
}

func TestRoam_GetUnion(t *testing.T) {
	roam := NewRoam()

	roam.Put("score", 85)
	roam.Put("ref", "@score")
	roam.PutMulti("nested.value", 100) // Use PutMulti for nested keys
	roam.Put("nestedRef", "@nested.value")

	// Test direct value
	if roam.GetUnion("direct") != "direct" {
		t.Error("expected direct value")
	}

	// Test reference
	if roam.GetUnion(roam.Get("ref")) != 85 {
		t.Error("expected @score to resolve to 85")
	}

	// Test nested reference
	if roam.GetUnion(roam.Get("nestedRef")) != 100 {
		t.Error("expected @nested.value to resolve to 100")
	}

	// Test nil
	if roam.GetUnion(nil) != nil {
		t.Error("expected nil for nil input")
	}
}

func TestRoam_TypeConversion(t *testing.T) {
	roam := NewRoam()

	// Store various numeric types
	roam.Put("int", 42)
	roam.Put("int64", int64(100))
	roam.Put("float64", 3.14)

	// GetInt conversions
	if roam.GetInt("int", 0) != 42 {
		t.Error("expected int=42")
	}
	if roam.GetInt("int64", 0) != 100 {
		t.Error("expected int64 converted to int=100")
	}
	if roam.GetInt("float64", 0) != 3 {
		t.Error("expected float64 converted to int=3")
	}
	if roam.GetInt("notexist", -1) != -1 {
		t.Error("expected default value -1")
	}

	// GetInt64 conversions
	if roam.GetInt64("int", 0) != 42 {
		t.Error("expected int converted to int64=42")
	}
	if roam.GetInt64("int64", 0) != 100 {
		t.Error("expected int64=100")
	}

	// GetFloat64 conversions
	if roam.GetFloat64("int", 0) != 42.0 {
		t.Error("expected int converted to float64=42.0")
	}
	if roam.GetFloat64("float64", 0) != 3.14 {
		t.Error("expected float64=3.14")
	}
}

func TestRoam_ShallowCopy(t *testing.T) {
	roam := NewRoam()
	roam.Put("key1", "value1")
	roam.Put("key2", 100)

	copied := roam.ShallowCopy()

	// Verify copy has same values
	if copied.GetString("key1") != "value1" {
		t.Error("copy should have key1=value1")
	}
	if copied.GetInt("key2", 0) != 100 {
		t.Error("copy should have key2=100")
	}

	// Modify copy should not affect original
	copied.Put("key1", "modified")
	if roam.GetString("key1") != "value1" {
		t.Error("original should not be modified")
	}
}

func TestRoam_Data(t *testing.T) {
	roam := NewRoam()
	roam.Put("a", 1)
	roam.Put("b", 2)

	data := roam.Data()
	if len(data) != 2 {
		t.Errorf("expected 2 entries, got %d", len(data))
	}
	if data["a"] != 1 || data["b"] != 2 {
		t.Error("data values mismatch")
	}

	// Modify returned data should not affect original
	data["a"] = 999
	if roam.GetInt("a", 0) != 1 {
		t.Error("original should not be modified")
	}
}

func TestRoam_ConcurrentAccess(t *testing.T) {
	roam := NewRoam()
	var wg sync.WaitGroup

	// Concurrent writes
	for i := 0; i < 100; i++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			roam.Put("key", n)
		}(i)
	}

	// Concurrent reads
	for i := 0; i < 100; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_ = roam.Get("key")
		}()
	}

	wg.Wait()

	// Should not panic and should have a value
	if roam.Get("key") == nil {
		t.Error("key should have a value after concurrent writes")
	}
}

func TestRoam_NewRoamFrom(t *testing.T) {
	original := map[string]any{
		"name": "test",
		"age":  25,
	}

	roam := NewRoamFrom(original)

	if roam.GetString("name") != "test" {
		t.Error("expected name=test")
	}
	if roam.GetInt("age", 0) != 25 {
		t.Error("expected age=25")
	}

	// Modify original should not affect roam
	original["name"] = "modified"
	if roam.GetString("name") != "test" {
		t.Error("roam should not be affected by original modification")
	}
}
