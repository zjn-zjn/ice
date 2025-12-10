// Package context provides execution context types for ice.
package context

import (
	"encoding/json"
	"reflect"
	"strings"
	"sync"
)

// Roam is a thread-safe map for storing business data during execution.
// It supports multi-key access (e.g., "a.b.c") and union references (e.g., "@key").
type Roam struct {
	mu   sync.RWMutex
	data map[string]any
}

// NewRoam creates a new Roam instance.
func NewRoam() *Roam {
	return &Roam{
		data: make(map[string]any),
	}
}

// NewRoamFrom creates a new Roam from an existing map (shallow copy).
func NewRoamFrom(m map[string]any) *Roam {
	r := &Roam{
		data: make(map[string]any, len(m)),
	}
	for k, v := range m {
		r.data[k] = v
	}
	return r
}

// Put stores a value with the given key. Returns the Roam for chaining.
func (r *Roam) Put(key string, value any) *Roam {
	if key == "" || value == nil {
		return r
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.data[key] = value
	return r
}

// Get retrieves a value by key.
func (r *Roam) Get(key string) any {
	if key == "" {
		return nil
	}
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.data[key]
}

// GetDefault retrieves a value by key, returning defaultVal if not found.
func (r *Roam) GetDefault(key string, defaultVal any) any {
	v := r.Get(key)
	if v == nil {
		return defaultVal
	}
	return v
}

// PutMulti stores a value using a dot-separated key path (e.g., "a.b.c").
func (r *Roam) PutMulti(multiKey string, value any) any {
	if multiKey == "" || value == nil {
		return nil
	}
	keys := strings.Split(multiKey, ".")
	if len(keys) == 1 {
		r.mu.Lock()
		defer r.mu.Unlock()
		old := r.data[keys[0]]
		r.data[keys[0]] = value
		return old
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	endMap := r.data
	for i := 0; i < len(keys)-1; i++ {
		v, ok := endMap[keys[i]]
		if !ok {
			newMap := make(map[string]any)
			endMap[keys[i]] = newMap
			endMap = newMap
		} else if m, ok := v.(map[string]any); ok {
			endMap = m
		} else {
			newMap := make(map[string]any)
			endMap[keys[i]] = newMap
			endMap = newMap
		}
	}
	old := endMap[keys[len(keys)-1]]
	endMap[keys[len(keys)-1]] = value
	return old
}

// GetMulti retrieves a value using a dot-separated key path (e.g., "a.b.c").
func (r *Roam) GetMulti(multiKey string) any {
	if multiKey == "" {
		return nil
	}
	keys := strings.Split(multiKey, ".")
	if len(keys) == 1 {
		return r.Get(keys[0])
	}

	r.mu.RLock()
	defer r.mu.RUnlock()

	var end any = r.data
	for _, key := range keys {
		if m, ok := end.(map[string]any); ok {
			end = m[key]
			if end == nil {
				return nil
			}
		} else {
			return nil
		}
	}
	return end
}

// GetUnion retrieves a value, supporting "@key" syntax to reference another key.
func (r *Roam) GetUnion(union any) any {
	if union == nil {
		return nil
	}
	if s, ok := union.(string); ok && len(s) > 0 && s[0] == '@' {
		return r.GetUnion(r.GetMulti(s[1:]))
	}
	return union
}

// GetString retrieves a string value.
func (r *Roam) GetString(key string) string {
	v := r.Get(key)
	if s, ok := v.(string); ok {
		return s
	}
	return ""
}

// GetInt retrieves an int value.
func (r *Roam) GetInt(key string, defaultVal int) int {
	v := r.Get(key)
	switch n := v.(type) {
	case int:
		return n
	case int64:
		return int(n)
	case float64:
		return int(n)
	default:
		return defaultVal
	}
}

// GetInt64 retrieves an int64 value.
func (r *Roam) GetInt64(key string, defaultVal int64) int64 {
	v := r.Get(key)
	switch n := v.(type) {
	case int64:
		return n
	case int:
		return int64(n)
	case float64:
		return int64(n)
	default:
		return defaultVal
	}
}

// GetFloat64 retrieves a float64 value.
func (r *Roam) GetFloat64(key string, defaultVal float64) float64 {
	v := r.Get(key)
	switch n := v.(type) {
	case float64:
		return n
	case int:
		return float64(n)
	case int64:
		return float64(n)
	default:
		return defaultVal
	}
}

// GetBool retrieves a bool value.
func (r *Roam) GetBool(key string) bool {
	v := r.Get(key)
	if b, ok := v.(bool); ok {
		return b
	}
	return false
}

// GetTo retrieves a value and assigns it to dest (must be a pointer).
// Returns true if the value was found and successfully assigned.
//
// Example:
//
//	var user *UserInfo
//	if roam.GetTo("user", &user) {
//	    fmt.Println(user.Name)
//	}
func (r *Roam) GetTo(key string, dest any) bool {
	return assignTo(r.Get(key), dest)
}

// GetMultiTo retrieves a value using dot-separated key and assigns it to dest.
// Returns true if the value was found and successfully assigned.
func (r *Roam) GetMultiTo(multiKey string, dest any) bool {
	return assignTo(r.GetMulti(multiKey), dest)
}

// assignTo assigns src to dest using reflection.
func assignTo(src, dest any) bool {
	if src == nil {
		return false
	}
	dv := reflect.ValueOf(dest)
	if dv.Kind() != reflect.Ptr || dv.IsNil() {
		return false
	}
	sv := reflect.ValueOf(src)
	elem := dv.Elem()
	if sv.Type().AssignableTo(elem.Type()) {
		elem.Set(sv)
		return true
	}
	// Handle pointer to value assignment (e.g., src is *T, dest is **T)
	if sv.Kind() == reflect.Ptr && sv.Type().Elem().AssignableTo(elem.Type()) {
		elem.Set(sv.Elem())
		return true
	}
	return false
}

// Value returns a RoamValue for fluent API access.
//
// Example:
//
//	name := roam.Value("name").String()
//	age := roam.Value("age").IntOr(18)
//	var user *UserInfo
//	roam.Value("user").To(&user)
func (r *Roam) Value(key string) *RoamValue {
	return &RoamValue{value: r.Get(key)}
}

// ValueMulti returns a RoamValue for fluent API access using dot-separated key.
func (r *Roam) ValueMulti(multiKey string) *RoamValue {
	return &RoamValue{value: r.GetMulti(multiKey)}
}

// RoamValue wraps a value for fluent API operations.
type RoamValue struct {
	value any
}

// Raw returns the underlying value.
func (rv *RoamValue) Raw() any {
	return rv.value
}

// Exists returns true if the value is not nil.
func (rv *RoamValue) Exists() bool {
	return rv.value != nil
}

// To assigns the value to dest (must be a pointer).
// Returns true if successful.
func (rv *RoamValue) To(dest any) bool {
	return assignTo(rv.value, dest)
}

// String returns the value as string, or empty string if not a string.
func (rv *RoamValue) String() string {
	if s, ok := rv.value.(string); ok {
		return s
	}
	return ""
}

// StringOr returns the value as string, or defaultVal if not a string.
func (rv *RoamValue) StringOr(defaultVal string) string {
	if s, ok := rv.value.(string); ok {
		return s
	}
	return defaultVal
}

// Int returns the value as int, or 0 if conversion fails.
func (rv *RoamValue) Int() int {
	return rv.IntOr(0)
}

// IntOr returns the value as int, or defaultVal if conversion fails.
func (rv *RoamValue) IntOr(defaultVal int) int {
	switch n := rv.value.(type) {
	case int:
		return n
	case int64:
		return int(n)
	case float64:
		return int(n)
	default:
		return defaultVal
	}
}

// Int64 returns the value as int64, or 0 if conversion fails.
func (rv *RoamValue) Int64() int64 {
	return rv.Int64Or(0)
}

// Int64Or returns the value as int64, or defaultVal if conversion fails.
func (rv *RoamValue) Int64Or(defaultVal int64) int64 {
	switch n := rv.value.(type) {
	case int64:
		return n
	case int:
		return int64(n)
	case float64:
		return int64(n)
	default:
		return defaultVal
	}
}

// Float64 returns the value as float64, or 0 if conversion fails.
func (rv *RoamValue) Float64() float64 {
	return rv.Float64Or(0)
}

// Float64Or returns the value as float64, or defaultVal if conversion fails.
func (rv *RoamValue) Float64Or(defaultVal float64) float64 {
	switch n := rv.value.(type) {
	case float64:
		return n
	case int:
		return float64(n)
	case int64:
		return float64(n)
	default:
		return defaultVal
	}
}

// Bool returns the value as bool, or false if not a bool.
func (rv *RoamValue) Bool() bool {
	if b, ok := rv.value.(bool); ok {
		return b
	}
	return false
}

// BoolOr returns the value as bool, or defaultVal if not a bool.
func (rv *RoamValue) BoolOr(defaultVal bool) bool {
	if b, ok := rv.value.(bool); ok {
		return b
	}
	return defaultVal
}

// Data returns a copy of the underlying map.
func (r *Roam) Data() map[string]any {
	r.mu.RLock()
	defer r.mu.RUnlock()
	result := make(map[string]any, len(r.data))
	for k, v := range r.data {
		result[k] = v
	}
	return result
}

// ShallowCopy creates a shallow copy of the Roam.
func (r *Roam) ShallowCopy() *Roam {
	return NewRoamFrom(r.Data())
}

// String returns the JSON representation of the Roam data.
func (r *Roam) String() string {
	b, err := json.Marshal(r.Data())
	if err != nil {
		return "{}"
	}
	return string(b)
}

// MarshalJSON implements json.Marshaler.
func (r *Roam) MarshalJSON() ([]byte, error) {
	return json.Marshal(r.Data())
}
