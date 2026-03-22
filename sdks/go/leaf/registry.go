// Package leaf provides leaf node registration and auto-adaptation.
package leaf

import (
	stdctx "context"
	"encoding/json"
	"fmt"
	"reflect"
	"runtime"
	"strings"
	"sync"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/dto"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/node"
	"github.com/zjn-zjn/ice/sdks/go/scan"
)

// LeafMeta contains metadata about a leaf node.
type LeafMeta struct {
	Name  string
	Desc  string
	Order int
	// Alias provides alternative names for this leaf class.
	// Useful for multi-language compatibility (e.g., Java/Go/Python using different class names).
	Alias []string
}

// AfterPropertiesSet is an optional interface that leaf nodes can implement
// to perform initialization after all properties have been set.
// This is called after JSON configuration is applied to the node.
type AfterPropertiesSet interface {
	AfterPropertiesSet()
}

// LeafErrorHandler is an optional interface that leaf nodes can implement
// to provide custom error handling logic when an error occurs during execution.
// The returned RunState determines the behavior:
//   - SHUT_DOWN: re-panic the error and stop execution
//   - TRUE/FALSE/NONE: use this state and continue execution
type LeafErrorHandler interface {
	ErrorHandle(ctx stdctx.Context, roam *icecontext.Roam, err error) enum.RunState
}

// LeafFlow is implemented by flow-type leaf nodes.
type LeafFlow interface {
	DoFlow(ctx stdctx.Context, roam *icecontext.Roam) bool
}

// LeafResult is implemented by result-type leaf nodes.
type LeafResult interface {
	DoResult(ctx stdctx.Context, roam *icecontext.Roam) bool
}

// LeafNone is implemented by none-type leaf nodes.
type LeafNone interface {
	DoNone(ctx stdctx.Context, roam *icecontext.Roam)
}

// leafEntry holds registration info for a leaf type.
type leafEntry struct {
	meta       *LeafMeta
	factory    func() any
	nodeType   enum.NodeType
	iceFields  []dto.IceFieldInfo
	hideFields []dto.IceFieldInfo
	roamKeys   []dto.RoamKeyMeta
}

// LeafNode is a node that can have its base properties set.
type LeafNode interface {
	node.Node
	node.BaseAccessor
	SetBase(base *node.Base)
}

var (
	registry      = make(map[string]*leafEntry)
	aliasRegistry = make(map[string]string) // alias -> className
	roamKeysStore = make(map[string][]dto.RoamKeyMeta) // className -> roamKeys (populated by generated code)
	mu            sync.RWMutex
)

// SetRoamKeys sets the roam key metadata for a given class name.
// This is typically called from generated code (roam_keys_gen.go) during init().
func SetRoamKeys(className string, keys []dto.RoamKeyMeta) {
	mu.Lock()
	defer mu.Unlock()
	roamKeysStore[className] = keys
	// Also update existing entry if already registered
	if entry, ok := registry[className]; ok {
		entry.roamKeys = keys
	}
}

// ApplyRoamKeysMap applies a map of short struct names to roam key metadata.
// It matches short names (e.g. "ScoreFlow") to registered full class names
// (e.g. "com.ice.test.flow.ScoreFlow") by suffix.
func ApplyRoamKeysMap(m map[string][]dto.RoamKeyMeta) {
	mu.Lock()
	defer mu.Unlock()
	for shortName, keys := range m {
		suffix := "." + shortName
		for fullName, entry := range registry {
			if strings.HasSuffix(fullName, suffix) || fullName == shortName {
				roamKeysStore[fullName] = keys
				entry.roamKeys = keys
				break
			}
		}
	}
}

// Register registers a leaf node factory with the given class name.
// The factory should return a pointer to a struct that implements one of:
// - LeafFlow (for flow type)
// - LeafResult (for result type)
// - LeafNone (for none type)
//
// Fields are automatically extracted from the struct using reflection:
// - Fields with `ice:"name:xxx,desc:xxx"` tag become iceFields (visible in UI)
// - Other fields with `json` tag become hideFields
//
// If meta.Alias is provided, those names will also be registered as aliases
// pointing to this class name.
func Register(className string, meta *LeafMeta, factory func() any) {
	if className == "" || factory == nil {
		return
	}

	// Create a sample to detect the type and extract fields
	sample := factory()
	nodeType := detectNodeType(sample)
	iceFields, hideFields := extractFields(sample)

	// Use pre-registered roam keys (from generated code), or auto-scan from source
	roamKeys := roamKeysStore[className]
	if len(roamKeys) == 0 {
		roamKeys = autoScanRoamKeys(sample)
	}

	mu.Lock()
	defer mu.Unlock()
	registry[className] = &leafEntry{
		meta:       meta,
		factory:    factory,
		nodeType:   nodeType,
		iceFields:  iceFields,
		hideFields: hideFields,
		roamKeys:   roamKeys,
	}

	// Register aliases
	if meta != nil {
		for _, alias := range meta.Alias {
			if alias != "" && alias != className {
				aliasRegistry[alias] = className
			}
		}
	}
}

// autoScanRoamKeys locates the source file where the leaf's business method
// (DoFlow/DoResult/DoNone) is defined via runtime reflection, then AST-scans it.
// Returns nil silently if source is unavailable (e.g., production deployment).
func autoScanRoamKeys(sample any) []dto.RoamKeyMeta {
	t := reflect.TypeOf(sample)
	file := findMethodSourceFile(t, "DoFlow", "DoResult", "DoNone")
	if file == "" {
		return nil
	}

	results, err := scan.ScanFile(file)
	if err != nil {
		return nil
	}

	if t.Kind() == reflect.Ptr {
		t = t.Elem()
	}
	structName := t.Name()

	for _, r := range results {
		if r.ClassName == structName {
			return r.RoamKeys
		}
	}
	return nil
}

// findMethodSourceFile returns the source file path where one of the named methods is defined.
func findMethodSourceFile(t reflect.Type, methodNames ...string) string {
	for _, name := range methodNames {
		m, ok := t.MethodByName(name)
		if !ok {
			continue
		}
		pc := m.Func.Pointer()
		fn := runtime.FuncForPC(pc)
		if fn == nil {
			continue
		}
		file, _ := fn.FileLine(pc)
		return file
	}
	return ""
}

// ResolveClassName resolves a config name to the actual class name.
// If confName is an alias, returns the real class name.
// Otherwise returns confName unchanged.
func ResolveClassName(confName string) string {
	mu.RLock()
	defer mu.RUnlock()
	if realName, ok := aliasRegistry[confName]; ok {
		return realName
	}
	return confName
}

// extractFields extracts field information from a struct using reflection.
// Fields with `ice` tag become iceFields, others become hideFields.
func extractFields(sample any) (iceFields, hideFields []dto.IceFieldInfo) {
	v := reflect.ValueOf(sample)
	if v.Kind() == reflect.Ptr {
		v = v.Elem()
	}
	if v.Kind() != reflect.Struct {
		return nil, nil
	}

	t := v.Type()
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)

		// Skip unexported fields
		if !field.IsExported() {
			continue
		}

		// Get json field name
		jsonTag := field.Tag.Get("json")
		if jsonTag == "" || jsonTag == "-" {
			continue
		}
		jsonName := strings.Split(jsonTag, ",")[0]

		// Get ice tag for iceFields
		iceTag := field.Tag.Get("ice")
		if iceTag == "-" {
			continue // ice:"-" means completely ignore this field
		}
		if iceTag != "" {
			// Parse ice tag: ice:"name:xxx,desc:xxx"
			fieldInfo := dto.IceFieldInfo{
				Field: jsonName,
				Type:  getTypeName(field.Type),
			}
			for _, part := range strings.Split(iceTag, ",") {
				kv := strings.SplitN(part, ":", 2)
				if len(kv) == 2 {
					switch kv[0] {
					case "name":
						fieldInfo.Name = kv[1]
					case "desc":
						fieldInfo.Desc = kv[1]
					}
				}
			}
			iceFields = append(iceFields, fieldInfo)
		} else {
			// hideField
			hideFields = append(hideFields, dto.IceFieldInfo{
				Field: jsonName,
				Type:  getTypeName(field.Type),
			})
		}
	}
	return
}

// getTypeName returns a language-agnostic type name.
func getTypeName(t reflect.Type) string {
	switch t.Kind() {
	case reflect.String:
		return "string"
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32:
		return "int"
	case reflect.Int64:
		return "long"
	case reflect.Float32:
		return "float"
	case reflect.Float64:
		return "double"
	case reflect.Bool:
		return "boolean"
	case reflect.Slice, reflect.Array:
		return "list"
	case reflect.Map:
		return "map"
	default:
		return "object"
	}
}

// detectNodeType detects the node type based on the interfaces implemented.
func detectNodeType(leaf any) enum.NodeType {
	if _, ok := leaf.(LeafFlow); ok {
		return enum.TypeLeafFlow
	}
	if _, ok := leaf.(LeafResult); ok {
		return enum.TypeLeafResult
	}
	if _, ok := leaf.(LeafNone); ok {
		return enum.TypeLeafNone
	}
	return enum.TypeLeafNone
}

// CreateNode creates a leaf node from configuration.
// confName can be a class name or an alias.
func CreateNode(confName, confField string) (node.Node, error) {
	mu.RLock()
	// First, try to resolve alias
	className := confName
	if realName, ok := aliasRegistry[confName]; ok {
		className = realName
	}
	entry, ok := registry[className]
	mu.RUnlock()

	if !ok {
		return nil, fmt.Errorf("leaf class not found: %s (resolved: %s)", confName, className)
	}

	// Create instance
	instance := entry.factory()

	// Apply configuration
	if confField != "" && confField != "{}" {
		if err := json.Unmarshal([]byte(confField), instance); err != nil {
			return nil, fmt.Errorf("failed to unmarshal leaf config: %w", err)
		}
	}

	// Call AfterPropertiesSet if implemented
	if aps, ok := instance.(AfterPropertiesSet); ok {
		aps.AfterPropertiesSet()
	}

	// Wrap with appropriate adapter
	return wrapLeaf(instance, entry.nodeType), nil
}

// wrapLeaf wraps a leaf instance with the appropriate node wrapper.
func wrapLeaf(instance any, nodeType enum.NodeType) node.Node {
	switch nodeType {
	case enum.TypeLeafFlow:
		return &flowLeafNode{instance: instance}
	case enum.TypeLeafResult:
		return &resultLeafNode{instance: instance}
	case enum.TypeLeafNone:
		return &noneLeafNode{instance: instance}
	default:
		return &noneLeafNode{instance: instance}
	}
}

// flowLeafNode wraps a flow-type leaf.
type flowLeafNode struct {
	node.Leaf
	instance any
}

// SetBase sets the base properties for this leaf node.
func (f *flowLeafNode) SetBase(base *node.Base) {
	f.Base = *base
}

func (f *flowLeafNode) Process(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	return node.ProcessWithBase(ctx, &f.Base, roam, f.doLeaf, f.getErrorHandler())
}

// getErrorHandler returns the error handler if the instance implements LeafErrorHandler.
func (f *flowLeafNode) getErrorHandler() node.ErrorHandler {
	if handler, ok := f.instance.(LeafErrorHandler); ok {
		return &leafErrorHandlerAdapter{handler: handler}
	}
	return nil
}

func (f *flowLeafNode) doLeaf(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	if leaf, ok := f.instance.(LeafFlow); ok {
		if leaf.DoFlow(ctx, roam) {
			return enum.TRUE
		}
		return enum.FALSE
	}
	return enum.FALSE
}

// leafErrorHandlerAdapter adapts LeafErrorHandler to node.ErrorHandler.
type leafErrorHandlerAdapter struct {
	handler LeafErrorHandler
}

func (a *leafErrorHandlerAdapter) ErrorHandle(ctx stdctx.Context, roam *icecontext.Roam, err error) enum.RunState {
	return a.handler.ErrorHandle(ctx, roam, err)
}

// resultLeafNode wraps a result-type leaf.
type resultLeafNode struct {
	node.Leaf
	instance any
}

// SetBase sets the base properties for this leaf node.
func (r *resultLeafNode) SetBase(base *node.Base) {
	r.Base = *base
}

func (r *resultLeafNode) Process(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	return node.ProcessWithBase(ctx, &r.Base, roam, r.doLeaf, r.getErrorHandler())
}

// getErrorHandler returns the error handler if the instance implements LeafErrorHandler.
func (r *resultLeafNode) getErrorHandler() node.ErrorHandler {
	if handler, ok := r.instance.(LeafErrorHandler); ok {
		return &leafErrorHandlerAdapter{handler: handler}
	}
	return nil
}

func (r *resultLeafNode) doLeaf(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	if leaf, ok := r.instance.(LeafResult); ok {
		if leaf.DoResult(ctx, roam) {
			return enum.TRUE
		}
		return enum.FALSE
	}
	return enum.FALSE
}

// noneLeafNode wraps a none-type leaf.
type noneLeafNode struct {
	node.Leaf
	instance any
}

// SetBase sets the base properties for this leaf node.
func (n *noneLeafNode) SetBase(base *node.Base) {
	n.Base = *base
}

func (n *noneLeafNode) Process(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	return node.ProcessWithBase(ctx, &n.Base, roam, n.doLeaf, n.getErrorHandler())
}

// getErrorHandler returns the error handler if the instance implements LeafErrorHandler.
func (n *noneLeafNode) getErrorHandler() node.ErrorHandler {
	if handler, ok := n.instance.(LeafErrorHandler); ok {
		return &leafErrorHandlerAdapter{handler: handler}
	}
	return nil
}

func (n *noneLeafNode) doLeaf(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	if leaf, ok := n.instance.(LeafNone); ok {
		leaf.DoNone(ctx, roam)
	}
	return enum.NONE
}

// GetLeafNodes returns information about all registered leaf nodes.
func GetLeafNodes() []dto.LeafNodeInfo {
	mu.RLock()
	defer mu.RUnlock()

	result := make([]dto.LeafNodeInfo, 0, len(registry))
	for className, entry := range registry {
		info := dto.LeafNodeInfo{
			Type:       byte(entry.nodeType),
			Clazz:      className,
			Order:      100,
			IceFields:  entry.iceFields,
			HideFields: entry.hideFields,
			RoamKeys:   entry.roamKeys,
		}
		if entry.meta != nil {
			info.Name = entry.meta.Name
			info.Desc = entry.meta.Desc
			if entry.meta.Order > 0 {
				info.Order = entry.meta.Order
			}
		}
		result = append(result, info)
	}
	return result
}

// IsRegistered checks if a leaf class is registered (including aliases).
func IsRegistered(className string) bool {
	mu.RLock()
	defer mu.RUnlock()
	// Check if it's directly registered
	if _, ok := registry[className]; ok {
		return true
	}
	// Check if it's an alias
	if realName, ok := aliasRegistry[className]; ok {
		_, exists := registry[realName]
		return exists
	}
	return false
}

// GetAliasRegistry returns a copy of the alias registry (for debugging).
func GetAliasRegistry() map[string]string {
	mu.RLock()
	defer mu.RUnlock()
	result := make(map[string]string, len(aliasRegistry))
	for k, v := range aliasRegistry {
		result[k] = v
	}
	return result
}
