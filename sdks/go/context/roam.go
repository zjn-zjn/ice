// Package context provides execution context types for ice.
package context

import (
	"encoding/json"
	"reflect"
	"strconv"
	"strings"
	"sync"

	"github.com/spf13/cast"
)

// Roam is a thread-safe map for storing business data during execution.
// It supports deep-key access (e.g., "a.b.c") and resolve references (e.g., "@key").
type Roam struct {
	mu   sync.RWMutex
	data map[string]any
	meta *Meta
}

// NewRoam creates a new Roam instance with default metadata.
func NewRoam() *Roam {
	return &Roam{
		data: make(map[string]any),
		meta: NewMeta(),
	}
}

// NewRoamFrom creates a new Roam from an existing map (shallow copy).
func NewRoamFrom(m map[string]any) *Roam {
	r := &Roam{
		data: make(map[string]any, len(m)),
		meta: NewMeta(),
	}
	for k, v := range m {
		r.data[k] = v
	}
	return r
}

// ============ meta getters ============

// GetMeta returns the metadata.
func (r *Roam) GetMeta() *Meta {
	return r.meta
}

// GetId returns the handler ID from metadata.
func (r *Roam) GetId() int64 {
	if r.meta == nil {
		return 0
	}
	return r.meta.Id
}

// GetScene returns the scene from metadata.
func (r *Roam) GetScene() string {
	if r.meta == nil {
		return ""
	}
	return r.meta.Scene
}

// GetTs returns the request timestamp from metadata.
func (r *Roam) GetTs() int64 {
	if r.meta == nil {
		return 0
	}
	return r.meta.Ts
}

// GetTrace returns the trace ID from metadata.
func (r *Roam) GetTrace() string {
	if r.meta == nil {
		return ""
	}
	return r.meta.Trace
}

// GetProcess returns the process info builder from metadata.
func (r *Roam) GetProcess() *strings.Builder {
	if r.meta == nil {
		return nil
	}
	return r.meta.Process
}

// GetDebug returns the debug flag from metadata.
func (r *Roam) GetDebug() byte {
	if r.meta == nil {
		return 0
	}
	return r.meta.Debug
}

// GetNid returns the node ID from metadata.
func (r *Roam) GetNid() int64 {
	if r.meta == nil {
		return 0
	}
	return r.meta.Nid
}

// ============ meta setters ============

// SetId sets the handler ID.
func (r *Roam) SetId(id int64) {
	if r.meta != nil {
		r.meta.Id = id
	}
}

// SetScene sets the scene.
func (r *Roam) SetScene(scene string) {
	if r.meta != nil {
		r.meta.Scene = scene
	}
}

// SetTs sets the request timestamp.
func (r *Roam) SetTs(ts int64) {
	if r.meta != nil {
		r.meta.Ts = ts
	}
}

// SetTrace sets the trace ID.
func (r *Roam) SetTrace(trace string) {
	if r.meta != nil {
		r.meta.Trace = trace
	}
}

// SetDebug sets the debug flag.
func (r *Roam) SetDebug(debug byte) {
	if r.meta != nil {
		r.meta.Debug = debug
	}
}

// SetNid sets the node ID.
func (r *Roam) SetNid(nid int64) {
	if r.meta != nil {
		r.meta.Nid = nid
	}
}

// ============ basic operations ============

// Put stores a value with the given key. Returns the Roam for chaining.
func (r *Roam) Put(key string, value any) *Roam {
	if key == "" {
		return r
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.data[key] = value
	return r
}

// PutAll stores all entries from the given map.
func (r *Roam) PutAll(data map[string]any) *Roam {
	if len(data) == 0 {
		return r
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	for k, v := range data {
		r.data[k] = v
	}
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

// Del removes a key from the Roam. Returns the Roam for chaining.
func (r *Roam) Del(key string) *Roam {
	if key == "" {
		return r
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	delete(r.data, key)
	return r
}

// PutDeep stores a value using a dot-separated key path (e.g., "a.b.c").
func (r *Roam) PutDeep(deepKey string, value any) any {
	if deepKey == "" {
		return nil
	}
	keys := strings.Split(deepKey, ".")
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

// DelDeep removes a value using a dot-separated key path (e.g., "a.b.c").
func (r *Roam) DelDeep(deepKey string) any {
	if deepKey == "" {
		return nil
	}
	keys := strings.Split(deepKey, ".")
	if len(keys) == 1 {
		r.mu.Lock()
		defer r.mu.Unlock()
		old := r.data[keys[0]]
		delete(r.data, keys[0])
		return old
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	var end any = r.data
	for i := 0; i < len(keys)-1; i++ {
		switch v := end.(type) {
		case map[string]any:
			end = v[keys[i]]
			if end == nil {
				return nil
			}
		case []any:
			idx, err := strconv.Atoi(keys[i])
			if err != nil || idx < 0 || idx >= len(v) {
				return nil
			}
			end = v[idx]
		default:
			return nil
		}
	}
	lastKey := keys[len(keys)-1]
	if container, ok := end.(map[string]any); ok {
		old := container[lastKey]
		delete(container, lastKey)
		return old
	}
	return nil
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

// StringE returns the value as string with error.
func (rv *RoamValue) StringE() (string, error) {
	return cast.ToStringE(rv.value)
}

// String returns the value as string, or empty string if conversion fails.
func (rv *RoamValue) String() string {
	return rv.StringOr("")
}

// StringOr returns the value as string, or defaultVal if conversion fails.
func (rv *RoamValue) StringOr(defaultVal string) string {
	v, err := rv.StringE()
	if rv.value == nil || err != nil {
		return defaultVal
	}
	return v
}

// IntE returns the value as int with error.
func (rv *RoamValue) IntE() (int, error) {
	return cast.ToIntE(rv.value)
}

// Int returns the value as int, or 0 if conversion fails.
func (rv *RoamValue) Int() int {
	return rv.IntOr(0)
}

// IntOr returns the value as int, or defaultVal if conversion fails.
func (rv *RoamValue) IntOr(defaultVal int) int {
	v, err := rv.IntE()
	if rv.value == nil || err != nil {
		return defaultVal
	}
	return v
}

// Int64E returns the value as int64 with error.
func (rv *RoamValue) Int64E() (int64, error) {
	return cast.ToInt64E(rv.value)
}

// Int64 returns the value as int64, or 0 if conversion fails.
func (rv *RoamValue) Int64() int64 {
	return rv.Int64Or(0)
}

// Int64Or returns the value as int64, or defaultVal if conversion fails.
func (rv *RoamValue) Int64Or(defaultVal int64) int64 {
	v, err := rv.Int64E()
	if rv.value == nil || err != nil {
		return defaultVal
	}
	return v
}

// Float64E returns the value as float64 with error.
func (rv *RoamValue) Float64E() (float64, error) {
	return cast.ToFloat64E(rv.value)
}

// Float64 returns the value as float64, or 0 if conversion fails.
func (rv *RoamValue) Float64() float64 {
	return rv.Float64Or(0)
}

// Float64Or returns the value as float64, or defaultVal if conversion fails.
func (rv *RoamValue) Float64Or(defaultVal float64) float64 {
	v, err := rv.Float64E()
	if rv.value == nil || err != nil {
		return defaultVal
	}
	return v
}

// BoolE returns the value as bool with error.
func (rv *RoamValue) BoolE() (bool, error) {
	return cast.ToBoolE(rv.value)
}

// Bool returns the value as bool, or false if conversion fails.
func (rv *RoamValue) Bool() bool {
	return rv.BoolOr(false)
}

// BoolOr returns the value as bool, or defaultVal if conversion fails.
func (rv *RoamValue) BoolOr(defaultVal bool) bool {
	v, err := rv.BoolE()
	if rv.value == nil || err != nil {
		return defaultVal
	}
	return v
}

// Data returns a copy of the underlying map (without metadata).
func (r *Roam) Data() map[string]any {
	r.mu.RLock()
	defer r.mu.RUnlock()
	result := make(map[string]any, len(r.data))
	for k, v := range r.data {
		result[k] = v
	}
	return result
}

// Clone creates a shallow copy of the Roam data with a cloned Meta (fresh Process builder).
func (r *Roam) Clone() *Roam {
	r.mu.RLock()
	defer r.mu.RUnlock()
	newRoam := &Roam{
		data: make(map[string]any, len(r.data)),
	}
	for k, v := range r.data {
		newRoam.data[k] = v
	}
	if r.meta != nil {
		newRoam.meta = &Meta{
			Id:      r.meta.Id,
			Scene:   r.meta.Scene,
			Nid:     r.meta.Nid,
			Ts:      r.meta.Ts,
			Trace:   r.meta.Trace,
			Debug:   r.meta.Debug,
			Process: &strings.Builder{},
		}
	}
	return newRoam
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
