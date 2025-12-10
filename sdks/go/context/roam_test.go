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

// Test types for GetTo and fluent API
type testUser struct {
	Name string
	Age  int
}

func TestRoam_GetTo(t *testing.T) {
	roam := NewRoam()

	user := &testUser{Name: "Alice", Age: 25}
	roam.Put("user", user)
	roam.Put("name", "Bob")
	roam.Put("count", 100)

	// Test GetTo with pointer type
	var gotUser *testUser
	if !roam.GetTo("user", &gotUser) {
		t.Error("GetTo should return true")
	}
	if gotUser.Name != "Alice" || gotUser.Age != 25 {
		t.Errorf("expected Alice/25, got %s/%d", gotUser.Name, gotUser.Age)
	}

	// Test GetTo with basic types
	var name string
	if !roam.GetTo("name", &name) {
		t.Error("GetTo should return true for string")
	}
	if name != "Bob" {
		t.Errorf("expected Bob, got %s", name)
	}

	var count int
	if !roam.GetTo("count", &count) {
		t.Error("GetTo should return true for int")
	}
	if count != 100 {
		t.Errorf("expected 100, got %d", count)
	}

	// Test GetTo with non-existent key
	var missing string
	if roam.GetTo("notexist", &missing) {
		t.Error("GetTo should return false for non-existent key")
	}

	// Test GetTo with nil dest
	if roam.GetTo("name", nil) {
		t.Error("GetTo should return false for nil dest")
	}
}

func TestRoam_GetMultiTo(t *testing.T) {
	roam := NewRoam()

	roam.PutMulti("user.profile.name", "Charlie")
	roam.PutMulti("user.profile.age", 30)

	var name string
	if !roam.GetMultiTo("user.profile.name", &name) {
		t.Error("GetMultiTo should return true")
	}
	if name != "Charlie" {
		t.Errorf("expected Charlie, got %s", name)
	}
}

func TestRoam_FluentAPI_Value(t *testing.T) {
	roam := NewRoam()

	roam.Put("name", "David")
	roam.Put("age", 28)
	roam.Put("score", 95.5)
	roam.Put("active", true)
	roam.Put("count", int64(1000))

	// Test String
	if roam.Value("name").String() != "David" {
		t.Error("expected name=David")
	}
	if roam.Value("notexist").String() != "" {
		t.Error("expected empty string for non-existent key")
	}
	if roam.Value("notexist").StringOr("default") != "default" {
		t.Error("expected default value")
	}

	// Test Int
	if roam.Value("age").Int() != 28 {
		t.Error("expected age=28")
	}
	if roam.Value("notexist").IntOr(99) != 99 {
		t.Error("expected default value 99")
	}

	// Test Int64
	if roam.Value("count").Int64() != 1000 {
		t.Error("expected count=1000")
	}

	// Test Float64
	if roam.Value("score").Float64() != 95.5 {
		t.Error("expected score=95.5")
	}
	if roam.Value("age").Float64() != 28.0 {
		t.Error("expected int converted to float64=28.0")
	}

	// Test Bool
	if !roam.Value("active").Bool() {
		t.Error("expected active=true")
	}
	if roam.Value("notexist").BoolOr(true) != true {
		t.Error("expected default value true")
	}

	// Test Exists
	if !roam.Value("name").Exists() {
		t.Error("expected name to exist")
	}
	if roam.Value("notexist").Exists() {
		t.Error("expected notexist to not exist")
	}

	// Test Raw
	if roam.Value("name").Raw() != "David" {
		t.Error("expected Raw() to return David")
	}
}

func TestRoam_FluentAPI_To(t *testing.T) {
	roam := NewRoam()

	user := &testUser{Name: "Eve", Age: 22}
	roam.Put("user", user)

	var gotUser *testUser
	if !roam.Value("user").To(&gotUser) {
		t.Error("To should return true")
	}
	if gotUser.Name != "Eve" || gotUser.Age != 22 {
		t.Errorf("expected Eve/22, got %s/%d", gotUser.Name, gotUser.Age)
	}

	// Test To with non-existent key
	var missing *testUser
	if roam.Value("notexist").To(&missing) {
		t.Error("To should return false for non-existent key")
	}
}

func TestRoam_FluentAPI_ValueMulti(t *testing.T) {
	roam := NewRoam()

	roam.PutMulti("config.server.port", 8080)
	roam.PutMulti("config.server.host", "localhost")

	if roam.ValueMulti("config.server.port").Int() != 8080 {
		t.Error("expected port=8080")
	}
	if roam.ValueMulti("config.server.host").String() != "localhost" {
		t.Error("expected host=localhost")
	}
}

func TestRoam_GetUnionTo(t *testing.T) {
	roam := NewRoam()

	user := &testUser{Name: "Frank", Age: 35}
	roam.Put("user", user)
	roam.Put("userRef", "@user")
	roam.Put("score", 88)
	roam.Put("scoreRef", "@score")

	// Test GetUnionTo with pointer type via reference
	var gotUser *testUser
	if !roam.GetUnionTo(roam.Get("userRef"), &gotUser) {
		t.Error("GetUnionTo should return true")
	}
	if gotUser.Name != "Frank" || gotUser.Age != 35 {
		t.Errorf("expected Frank/35, got %s/%d", gotUser.Name, gotUser.Age)
	}

	// Test GetUnionTo with basic type via reference
	var score int
	if !roam.GetUnionTo(roam.Get("scoreRef"), &score) {
		t.Error("GetUnionTo should return true for int")
	}
	if score != 88 {
		t.Errorf("expected 88, got %d", score)
	}

	// Test GetUnionTo with direct value (not a reference)
	var directScore int
	if !roam.GetUnionTo(roam.Get("score"), &directScore) {
		t.Error("GetUnionTo should return true for direct value")
	}
	if directScore != 88 {
		t.Errorf("expected 88, got %d", directScore)
	}

	// Test GetUnionTo with nil
	var missing *testUser
	if roam.GetUnionTo(nil, &missing) {
		t.Error("GetUnionTo should return false for nil union")
	}
}

func TestRoam_FluentAPI_ValueUnion(t *testing.T) {
	roam := NewRoam()

	roam.Put("name", "Grace")
	roam.Put("nameRef", "@name")
	roam.Put("age", 28)
	roam.Put("ageRef", "@age")
	roam.PutMulti("nested.value", 999)
	roam.Put("nestedRef", "@nested.value")

	user := &testUser{Name: "Helen", Age: 40}
	roam.Put("user", user)
	roam.Put("userRef", "@user")

	// Test ValueUnion with string reference
	if roam.ValueUnion(roam.Get("nameRef")).String() != "Grace" {
		t.Error("expected name=Grace via reference")
	}

	// Test ValueUnion with int reference
	if roam.ValueUnion(roam.Get("ageRef")).Int() != 28 {
		t.Error("expected age=28 via reference")
	}

	// Test ValueUnion with nested reference
	if roam.ValueUnion(roam.Get("nestedRef")).Int() != 999 {
		t.Error("expected nested.value=999 via reference")
	}

	// Test ValueUnion with To() for struct
	var gotUser *testUser
	if !roam.ValueUnion(roam.Get("userRef")).To(&gotUser) {
		t.Error("ValueUnion To should return true")
	}
	if gotUser.Name != "Helen" || gotUser.Age != 40 {
		t.Errorf("expected Helen/40, got %s/%d", gotUser.Name, gotUser.Age)
	}

	// Test ValueUnion with direct value (not a reference)
	if roam.ValueUnion(roam.Get("name")).String() != "Grace" {
		t.Error("expected direct value name=Grace")
	}

	// Test ValueUnion with nil
	if roam.ValueUnion(nil).Exists() {
		t.Error("ValueUnion(nil) should not exist")
	}
}
