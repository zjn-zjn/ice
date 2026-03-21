// Package context provides execution context types for ice.
package context

import (
	"encoding/json"
	"reflect"
	"strconv"
	"strings"
	"sync"
)

const iceMetaKey = "_ice"

// Roam is a thread-safe map for storing business data during execution.
// It supports deep-key access (e.g., "a.b.c") and resolve references (e.g., "@key").
// Ice metadata is stored under the reserved "_ice" key.
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

// NewRoamWithMeta creates a new Roam instance with default IceMeta.
func NewRoamWithMeta() *Roam {
	r := &Roam{
		data: make(map[string]any),
	}
	r.data[iceMetaKey] = NewMeta()
	return r
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

// GetMeta returns the IceMeta stored in this Roam.
func (r *Roam) GetMeta() *IceMeta {
	r.mu.RLock()
	defer r.mu.RUnlock()
	if m, ok := r.data[iceMetaKey].(*IceMeta); ok {
		return m
	}
	return nil
}

// GetIceId returns the ice handler ID from metadata.
func (r *Roam) GetIceId() int64 {
	if m := r.GetMeta(); m != nil {
		return m.Id
	}
	return 0
}

// GetIceScene returns the scene from metadata.
func (r *Roam) GetIceScene() string {
	if m := r.GetMeta(); m != nil {
		return m.Scene
	}
	return ""
}

// GetIceTs returns the request timestamp from metadata.
func (r *Roam) GetIceTs() int64 {
	if m := r.GetMeta(); m != nil {
		return m.Ts
	}
	return 0
}

// GetIceTrace returns the trace ID from metadata.
func (r *Roam) GetIceTrace() string {
	if m := r.GetMeta(); m != nil {
		return m.Trace
	}
	return ""
}

// GetIceProcess returns the process info builder from metadata.
func (r *Roam) GetIceProcess() *strings.Builder {
	if m := r.GetMeta(); m != nil {
		return m.Process
	}
	return nil
}

// GetIceDebug returns the debug flag from metadata.
func (r *Roam) GetIceDebug() byte {
	if m := r.GetMeta(); m != nil {
		return m.Debug
	}
	return 0
}

// Put stores a value with the given key. Returns the Roam for chaining.
// The "_ice" key is reserved for internal metadata and cannot be overwritten.
// Supports storing nil values.
func (r *Roam) Put(key string, value any) *Roam {
	if key == "" || key == iceMetaKey {
		return r
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.data[key] = value
	return r
}

// PutDirect stores a value with the given key without any key restrictions.
// This is for internal use only.
func (r *Roam) PutDirect(key string, value any) *Roam {
	if key == "" {
		return r
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.data[key] = value
	return r
}

// PutAll stores all entries from the given map. The "_ice" key is skipped.
func (r *Roam) PutAll(data map[string]any) *Roam {
	if len(data) == 0 {
		return r
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	for k, v := range data {
		if k == iceMetaKey {
			continue
		}
		r.data[k] = v
	}
	return r
}

// Delete removes a key from the Roam. Returns the Roam for chaining.
// The "_ice" key cannot be deleted.
func (r *Roam) Delete(key string) *Roam {
	if key == "" || key == iceMetaKey {
		return r
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	delete(r.data, key)
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

// PutDeep stores a value using a dot-separated key path (e.g., "a.b.c").
// The "_ice" key is reserved and cannot be overwritten via this method.
// Supports storing nil values.
func (r *Roam) PutDeep(deepKey string, value any) any {
	if deepKey == "" {
		return nil
	}
	keys := strings.Split(deepKey, ".")
	if keys[0] == iceMetaKey {
		return nil
	}
	if len(keys) == 1 {
		r.mu.Lock()
		defer r.mu.Unlock()
		old := r.data[keys[0]]
		r.data[keys[0]] = value
		return old
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	var end any = r.data
	for i := 0; i < len(keys)-1; i++ {
		switch container := end.(type) {
		case map[string]any:
			v, ok := container[keys[i]]
			if !ok {
				newMap := make(map[string]any)
				container[keys[i]] = newMap
				end = newMap
			} else if m, ok := v.(map[string]any); ok {
				end = m
			} else if arr, ok := v.([]any); ok {
				end = arr
			} else {
				newMap := make(map[string]any)
				container[keys[i]] = newMap
				end = newMap
			}
		case []any:
			idx, err := strconv.Atoi(keys[i])
			if err != nil || idx < 0 || idx >= len(container) {
				return nil
			}
			end = container[idx]
		default:
			return nil
		}
	}
	lastKey := keys[len(keys)-1]
	switch container := end.(type) {
	case map[string]any:
		old := container[lastKey]
		container[lastKey] = value
		return old
	case []any:
		idx, err := strconv.Atoi(lastKey)
		if err != nil || idx < 0 || idx >= len(container) {
			return nil
		}
		old := container[idx]
		container[idx] = value
		return old
	}
	return nil
}

// GetDeep retrieves a value using a dot-separated key path (e.g., "a.b.c").
func (r *Roam) GetDeep(deepKey string) any {
	if deepKey == "" {
		return nil
	}
	keys := strings.Split(deepKey, ".")
	if len(keys) == 1 {
		return r.Get(keys[0])
	}

	r.mu.RLock()
	defer r.mu.RUnlock()

	var end any = r.data
	for _, key := range keys {
		switch v := end.(type) {
		case map[string]any:
			end = v[key]
			if end == nil {
				return nil
			}
		case []any:
			idx, err := strconv.Atoi(key)
			if err != nil || idx < 0 || idx >= len(v) {
				return nil
			}
			end = v[idx]
		default:
			return nil
		}
	}
	return end
}

// Resolve retrieves a value, supporting "@key" syntax to reference another key.
func (r *Roam) Resolve(union any) any {
	if union == nil {
		return nil
	}
	if s, ok := union.(string); ok && len(s) > 0 && s[0] == '@' {
		return r.Resolve(r.GetDeep(s[1:]))
	}
	return union
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

// ValueDeep returns a RoamValue for fluent API access using dot-separated key.
func (r *Roam) ValueDeep(deepKey string) *RoamValue {
	return &RoamValue{value: r.GetDeep(deepKey)}
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

// Clone creates a shallow copy of the Roam data with a cloned IceMeta (fresh Process builder).
func (r *Roam) Clone() *Roam {
	r.mu.RLock()
	defer r.mu.RUnlock()
	newRoam := &Roam{
		data: make(map[string]any, len(r.data)),
	}
	for k, v := range r.data {
		if k == iceMetaKey {
			if meta, ok := v.(*IceMeta); ok {
				newRoam.data[k] = meta.Clone()
			}
			continue
		}
		newRoam.data[k] = v
	}
	return newRoam
}

// ShallowCopy creates a shallow copy of the Roam.
// Deprecated: Use Clone() instead for proper IceMeta handling.
func (r *Roam) ShallowCopy() *Roam {
	return r.Clone()
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
