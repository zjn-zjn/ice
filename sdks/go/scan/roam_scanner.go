// Package scan provides AST-based scanning of leaf node source code to extract roam key access metadata.
package scan

import (
	"fmt"
	"go/ast"
	"go/parser"
	"go/token"
	"os"
	"path/filepath"
	"strings"

	"github.com/zjn-zjn/ice/sdks/go/dto"
)

const maxDepth = 10

// roam method -> (direction, accessMode, accessMethod)
var readMethods = map[string][3]string{
	"Get":      {"read", "direct", "get"},
	"GetDeep":  {"read", "direct", "getDeep"},
	"Resolve":  {"read", "union", "get"},
	"Value":    {"read", "direct", "get"},
	"ValueDeep": {"read", "direct", "getDeep"},
}

var writeMethods = map[string][3]string{
	"Put":     {"write", "direct", "put"},
	"PutDeep": {"write", "direct", "putDeep"},
}

// ScanResult holds the scanned roam keys for a struct.
type ScanResult struct {
	ClassName string
	RoamKeys  []dto.RoamKeyMeta
}

// scanContext holds all declarations collected from a package for scanning.
type scanContext struct {
	structFields  map[string]map[string]bool          // structName -> field names
	structMethods map[string]map[string]*ast.FuncDecl // structName -> methodName -> funcDecl
	pkgFunctions  map[string]*ast.FuncDecl            // funcName -> funcDecl
}

func newScanContext() *scanContext {
	return &scanContext{
		structFields:  map[string]map[string]bool{},
		structMethods: map[string]map[string]*ast.FuncDecl{},
		pkgFunctions:  map[string]*ast.FuncDecl{},
	}
}

// collectDecls collects struct definitions, methods, and package-level functions from an AST file.
func collectDecls(file *ast.File, ctx *scanContext) {
	for _, decl := range file.Decls {
		switch d := decl.(type) {
		case *ast.FuncDecl:
			if d.Recv != nil && len(d.Recv.List) > 0 {
				recvType := resolveRecvType(d.Recv.List[0].Type)
				if recvType != "" {
					if ctx.structMethods[recvType] == nil {
						ctx.structMethods[recvType] = map[string]*ast.FuncDecl{}
					}
					ctx.structMethods[recvType][d.Name.Name] = d
				}
			} else {
				// Package-level function
				ctx.pkgFunctions[d.Name.Name] = d
			}
		case *ast.GenDecl:
			if d.Tok == token.TYPE {
				for _, spec := range d.Specs {
					ts, ok := spec.(*ast.TypeSpec)
					if !ok {
						continue
					}
					st, ok := ts.Type.(*ast.StructType)
					if !ok {
						continue
					}
					fields := map[string]bool{}
					for _, f := range st.Fields.List {
						for _, name := range f.Names {
							fields[name.Name] = true
						}
					}
					ctx.structFields[ts.Name.Name] = fields
				}
			}
		}
	}
}

// scanLeafs scans all collected declarations for leaf node roam key accesses.
func scanLeafs(ctx *scanContext) []ScanResult {
	targetMethodNames := map[string]bool{"DoFlow": true, "DoResult": true, "DoNone": true}

	var results []ScanResult
	for structName, methods := range ctx.structMethods {
		var targetFuncs []*ast.FuncDecl
		for name, fn := range methods {
			if targetMethodNames[name] {
				targetFuncs = append(targetFuncs, fn)
			}
		}
		if len(targetFuncs) == 0 {
			continue
		}

		var metas []dto.RoamKeyMeta
		fields := ctx.structFields[structName]

		for _, fn := range targetFuncs {
			roamParam := findRoamParam(fn)
			if roamParam == "" {
				continue
			}
			recvName := ""
			if fn.Recv != nil && len(fn.Recv.List) > 0 && len(fn.Recv.List[0].Names) > 0 {
				recvName = fn.Recv.List[0].Names[0].Name
			}

			visited := map[string]bool{}
			scanFuncBody(fn.Body, roamParam, recvName, fields, methods, ctx.pkgFunctions, &metas, visited, 0)
		}

		if len(metas) > 0 {
			metas = mergeDirections(metas)
			results = append(results, ScanResult{
				ClassName: structName,
				RoamKeys:  metas,
			})
		}
	}

	return results
}

// ScanFile scans a single Go source file for leaf structs and their roam key accesses.
func ScanFile(filePath string) ([]ScanResult, error) {
	fset := token.NewFileSet()
	f, err := parser.ParseFile(fset, filePath, nil, 0)
	if err != nil {
		return nil, fmt.Errorf("parse file %s: %w", filePath, err)
	}
	ctx := newScanContext()
	collectDecls(f, ctx)
	return scanLeafs(ctx), nil
}

// ScanPackage scans all Go files in the given directory for leaf structs and their roam key accesses.
// It collects struct methods and package-level functions across all files, enabling cross-file tracking.
func ScanPackage(dir string) ([]ScanResult, error) {
	fset := token.NewFileSet()
	pkgs, err := parser.ParseDir(fset, dir, func(info os.FileInfo) bool {
		return !strings.HasSuffix(info.Name(), "_test.go")
	}, parser.ParseComments)
	if err != nil {
		return nil, fmt.Errorf("parse dir %s: %w", dir, err)
	}

	var results []ScanResult
	for _, pkg := range pkgs {
		ctx := newScanContext()
		for _, file := range pkg.Files {
			collectDecls(file, ctx)
		}
		results = append(results, scanLeafs(ctx)...)
	}

	return results, nil
}

// ScanDir recursively scans all Go packages in the directory tree.
func ScanDir(root string) ([]ScanResult, error) {
	var allResults []ScanResult

	err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil // skip errors
		}
		if !info.IsDir() {
			return nil
		}
		// Skip vendor, hidden dirs
		base := filepath.Base(path)
		if strings.HasPrefix(base, ".") || base == "vendor" || base == "testdata" {
			return filepath.SkipDir
		}

		// Check if dir has Go files
		entries, err := os.ReadDir(path)
		if err != nil {
			return nil
		}
		hasGo := false
		for _, e := range entries {
			if !e.IsDir() && strings.HasSuffix(e.Name(), ".go") && !strings.HasSuffix(e.Name(), "_test.go") {
				hasGo = true
				break
			}
		}
		if !hasGo {
			return nil
		}

		res, err := ScanPackage(path)
		if err != nil {
			return nil // skip failed packages
		}
		allResults = append(allResults, res...)
		return nil
	})

	return allResults, err
}

func resolveRecvType(expr ast.Expr) string {
	switch t := expr.(type) {
	case *ast.StarExpr:
		if ident, ok := t.X.(*ast.Ident); ok {
			return ident.Name
		}
	case *ast.Ident:
		return t.Name
	}
	return ""
}

func findRoamParam(fn *ast.FuncDecl) string {
	if fn.Type.Params == nil {
		return ""
	}
	for _, param := range fn.Type.Params.List {
		if isRoamType(param.Type) {
			if len(param.Names) > 0 {
				return param.Names[0].Name
			}
		}
	}
	return ""
}

func isRoamType(expr ast.Expr) bool {
	switch t := expr.(type) {
	case *ast.StarExpr:
		return isRoamType(t.X)
	case *ast.SelectorExpr:
		return t.Sel.Name == "Roam"
	case *ast.Ident:
		return t.Name == "Roam"
	}
	return false
}

func scanFuncBody(
	body *ast.BlockStmt,
	roamParam, recvName string,
	fields map[string]bool,
	structMethods map[string]*ast.FuncDecl,
	pkgFunctions map[string]*ast.FuncDecl,
	metas *[]dto.RoamKeyMeta,
	visited map[string]bool,
	depth int,
) {
	if body == nil || depth > maxDepth {
		return
	}

	// Track local variable assignments
	assignments := map[string]ast.Expr{}

	ast.Inspect(body, func(n ast.Node) bool {
		switch node := n.(type) {
		case *ast.AssignStmt:
			// Track simple assignments: x := expr or x = expr
			for i, lhs := range node.Lhs {
				if ident, ok := lhs.(*ast.Ident); ok && i < len(node.Rhs) {
					assignments[ident.Name] = node.Rhs[i]
				}
			}

		case *ast.CallExpr:
			// Handle direct function calls: someFunc(roam)
			if ident, ok := node.Fun.(*ast.Ident); ok {
				if depth < maxDepth {
					checkPkgFunction(node, ident.Name, roamParam, recvName, fields, structMethods, pkgFunctions, metas, assignments, visited, depth)
				}
				return true
			}

			sel, ok := node.Fun.(*ast.SelectorExpr)
			if !ok {
				return true
			}

			// Check for chained read call: roam.ValueDeep(key).Int()
			if innerCall, ok := sel.X.(*ast.CallExpr); ok {
				if innerSel, ok := innerCall.Fun.(*ast.SelectorExpr); ok {
					if isRoamRef(innerSel.X, roamParam, assignments) {
						innerMethod := innerSel.Sel.Name
						if info, ok := readMethods[innerMethod]; ok && len(innerCall.Args) >= 1 {
							keyParts := resolveKey(innerCall.Args[0], recvName, fields, assignments, roamParam)
							if len(keyParts) > 0 {
								meta := dto.RoamKeyMeta{
									Direction:    info[0],
									AccessMode:   info[1],
									AccessMethod: info[2],
									KeyParts:     keyParts,
								}
								*metas = append(*metas, meta)
								return false // don't revisit inner call
							}
						}
					}
				}
			}

			// Check if this is a roam method call
			if isRoamRef(sel.X, roamParam, assignments) {
				methodName := sel.Sel.Name

				if info, ok := readMethods[methodName]; ok && len(node.Args) >= 1 {
					keyParts := resolveKey(node.Args[0], recvName, fields, assignments, roamParam)
					if len(keyParts) > 0 {
						meta := dto.RoamKeyMeta{
							Direction:    info[0],
							AccessMode:   info[1],
							AccessMethod: info[2],
							KeyParts:     keyParts,
						}
						*metas = append(*metas, meta)
					}
				} else if info, ok := writeMethods[methodName]; ok && len(node.Args) >= 2 {
					keyParts := resolveKey(node.Args[0], recvName, fields, assignments, roamParam)
					if len(keyParts) > 0 {
						meta := dto.RoamKeyMeta{
							Direction:    info[0],
							AccessMode:   info[1],
							AccessMethod: info[2],
							KeyParts:     keyParts,
						}
						*metas = append(*metas, meta)
					}
				}
			} else if depth < maxDepth {
				// Check for cross-method calls on self: p.helper(roam)
				checkCrossMethod(node, sel, roamParam, recvName, fields, structMethods, pkgFunctions, metas, assignments, visited, depth)
			}
		}
		return true
	})
}

func isRoamRef(expr ast.Expr, roamParam string, assignments map[string]ast.Expr) bool {
	return isRoamRefSeen(expr, roamParam, assignments, nil)
}

func isRoamRefSeen(expr ast.Expr, roamParam string, assignments map[string]ast.Expr, seen map[string]bool) bool {
	if ident, ok := expr.(*ast.Ident); ok {
		if ident.Name == roamParam {
			return true
		}
		if src, ok := assignments[ident.Name]; ok {
			if seen == nil {
				seen = map[string]bool{}
			}
			if seen[ident.Name] {
				return false
			}
			seen[ident.Name] = true
			return isRoamRefSeen(src, roamParam, assignments, seen)
		}
	}
	return false
}

func resolveKey(expr ast.Expr, recvName string, fields map[string]bool, assignments map[string]ast.Expr, roamParam string) []dto.KeyPart {
	return resolveKeySeen(expr, recvName, fields, assignments, roamParam, nil)
}

func resolveKeySeen(expr ast.Expr, recvName string, fields map[string]bool, assignments map[string]ast.Expr, roamParam string, seen map[string]bool) []dto.KeyPart {
	switch e := expr.(type) {
	case *ast.BasicLit:
		if e.Kind == token.STRING {
			// Remove quotes
			val := strings.Trim(e.Value, "\"'`")
			return []dto.KeyPart{{Type: "literal", Value: val}}
		}

	case *ast.SelectorExpr:
		if ident, ok := e.X.(*ast.Ident); ok && ident.Name == recvName {
			if fields[e.Sel.Name] {
				return []dto.KeyPart{{Type: "field", Ref: e.Sel.Name}}
			}
		}

	case *ast.Ident:
		if src, ok := assignments[e.Name]; ok {
			if seen == nil {
				seen = map[string]bool{}
			}
			if seen[e.Name] {
				return nil
			}
			seen[e.Name] = true
			return resolveKeySeen(src, recvName, fields, assignments, roamParam, seen)
		}

	case *ast.BinaryExpr:
		if e.Op == token.ADD {
			left := resolveKeySeen(e.X, recvName, fields, assignments, roamParam, seen)
			right := resolveKeySeen(e.Y, recvName, fields, assignments, roamParam, seen)
			if len(left) > 0 && len(right) > 0 {
				allParts := append(left, right...)
				return []dto.KeyPart{{Type: "composite", Parts: allParts}}
			}
		}

	case *ast.CallExpr:
		// Check if this is a roam read method call (e.g. roam.Get("key"))
		if sel, ok := e.Fun.(*ast.SelectorExpr); ok {
			if _, isRead := readMethods[sel.Sel.Name]; isRead && roamParam != "" {
				if isRoamRef(sel.X, roamParam, assignments) && len(e.Args) >= 1 {
					innerKey := resolveKeySeen(e.Args[0], recvName, fields, assignments, roamParam, seen)
					if len(innerKey) > 0 {
						fromKey := keyPartsToFromKey(innerKey)
						return []dto.KeyPart{{Type: "roamDerived", FromKey: fromKey}}
					}
				}
			}
			// fmt.Sprintf or similar string formatting
			if ident, ok := sel.X.(*ast.Ident); ok && ident.Name == "fmt" && sel.Sel.Name == "Sprintf" {
				return nil
			}
		}
	}

	return nil
}

// checkCrossMethod handles calls like p.helper(roam) where p is the receiver.
func checkCrossMethod(
	call *ast.CallExpr,
	sel *ast.SelectorExpr,
	roamParam, recvName string,
	fields map[string]bool,
	structMethods map[string]*ast.FuncDecl,
	pkgFunctions map[string]*ast.FuncDecl,
	metas *[]dto.RoamKeyMeta,
	assignments map[string]ast.Expr,
	visited map[string]bool,
	depth int,
) {
	// Check if receiver is self
	ident, ok := sel.X.(*ast.Ident)
	if !ok || ident.Name != recvName {
		return
	}

	// Check if any arg passes roam (including aliases)
	roamArgIdx := findRoamArgIndex(call.Args, roamParam, assignments)
	if roamArgIdx < 0 {
		return
	}

	methodName := sel.Sel.Name
	visitKey := recvName + "." + methodName
	if visited[visitKey] {
		return
	}
	visited[visitKey] = true

	fn, ok := structMethods[methodName]
	if !ok || fn.Body == nil {
		return
	}

	// Find roam param name in target method
	targetRoamParam := findParamNameByIndex(fn, roamArgIdx)
	if targetRoamParam == "" {
		return
	}

	targetRecvName := ""
	if fn.Recv != nil && len(fn.Recv.List) > 0 && len(fn.Recv.List[0].Names) > 0 {
		targetRecvName = fn.Recv.List[0].Names[0].Name
	}

	scanFuncBody(fn.Body, targetRoamParam, targetRecvName, fields, structMethods, pkgFunctions, metas, visited, depth+1)
}

// checkPkgFunction handles calls like someFunc(roam) to package-level functions.
func checkPkgFunction(
	call *ast.CallExpr,
	funcName, roamParam, recvName string,
	fields map[string]bool,
	structMethods map[string]*ast.FuncDecl,
	pkgFunctions map[string]*ast.FuncDecl,
	metas *[]dto.RoamKeyMeta,
	assignments map[string]ast.Expr,
	visited map[string]bool,
	depth int,
) {
	roamArgIdx := findRoamArgIndex(call.Args, roamParam, assignments)
	if roamArgIdx < 0 {
		return
	}

	visitKey := "pkg:" + funcName
	if visited[visitKey] {
		return
	}
	visited[visitKey] = true

	fn, ok := pkgFunctions[funcName]
	if !ok || fn.Body == nil {
		return
	}

	targetRoamParam := findParamNameByIndex(fn, roamArgIdx)
	if targetRoamParam == "" {
		return
	}

	scanFuncBody(fn.Body, targetRoamParam, recvName, fields, structMethods, pkgFunctions, metas, visited, depth+1)
}

// findRoamArgIndex returns the index of the first argument that references roam, or -1.
func findRoamArgIndex(args []ast.Expr, roamParam string, assignments map[string]ast.Expr) int {
	for i, arg := range args {
		if isRoamRef(arg, roamParam, assignments) {
			return i
		}
	}
	return -1
}

// findParamNameByIndex returns the parameter name at the given positional index.
func findParamNameByIndex(fn *ast.FuncDecl, idx int) string {
	if fn.Type.Params == nil {
		return ""
	}
	paramIdx := 0
	for _, param := range fn.Type.Params.List {
		for _, name := range param.Names {
			if paramIdx == idx {
				return name.Name
			}
			paramIdx++
		}
	}
	return ""
}

func keyPartsToFromKey(parts []dto.KeyPart) string {
	var sb strings.Builder
	for _, p := range parts {
		switch p.Type {
		case "literal":
			sb.WriteString(p.Value)
		case "field":
			sb.WriteString("${")
			sb.WriteString(p.Ref)
			sb.WriteByte('}')
		case "composite":
			sb.WriteString(keyPartsToFromKey(p.Parts))
		default:
			sb.WriteByte('?')
		}
	}
	return sb.String()
}

func mergeDirections(metas []dto.RoamKeyMeta) []dto.RoamKeyMeta {
	if len(metas) <= 1 {
		return metas
	}

	type entry struct {
		meta  *dto.RoamKeyMeta
		order int
	}
	merged := map[string]*entry{}
	idx := 0

	for i := range metas {
		sig := keySignature(&metas[i])
		if existing, ok := merged[sig]; ok {
			if existing.meta.Direction != metas[i].Direction {
				existing.meta.Direction = "read_write"
			}
		} else {
			merged[sig] = &entry{meta: &metas[i], order: idx}
			idx++
		}
	}

	result := make([]dto.RoamKeyMeta, 0, len(merged))
	// Maintain order
	ordered := make([]*entry, 0, len(merged))
	for _, e := range merged {
		ordered = append(ordered, e)
	}
	// Sort by order
	for i := 0; i < len(ordered); i++ {
		for j := i + 1; j < len(ordered); j++ {
			if ordered[j].order < ordered[i].order {
				ordered[i], ordered[j] = ordered[j], ordered[i]
			}
		}
	}
	for _, e := range ordered {
		result = append(result, *e.meta)
	}
	return result
}

func keySignature(meta *dto.RoamKeyMeta) string {
	var sb strings.Builder
	partsToSig(&sb, meta.KeyParts)
	return sb.String()
}

func partsToSig(sb *strings.Builder, parts []dto.KeyPart) {
	for _, p := range parts {
		sb.WriteByte('[')
		sb.WriteString(p.Type)
		sb.WriteByte('=')
		switch p.Type {
		case "literal":
			sb.WriteString(p.Value)
		case "field":
			sb.WriteString(p.Ref)
		case "roamDerived":
			sb.WriteString(p.FromKey)
		case "composite":
			partsToSig(sb, p.Parts)
		}
		sb.WriteByte(']')
	}
}
