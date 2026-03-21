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

const maxDepth = 3

// roam method -> (direction, accessMode, accessMethod)
var readMethods = map[string][3]string{
	"Get":        {"read", "direct", "get"},
	"GetDeep":   {"read", "direct", "getDeep"},
	"Resolve":   {"read", "union", "get"},
	"Value":     {"read", "direct", "get"},
	"ValueDeep": {"read", "direct", "getDeep"},
}

var writeMethods = map[string][3]string{
	"Put":      {"write", "direct", "put"},
	"PutDeep": {"write", "direct", "putDeep"},
}

// ScanResult holds the scanned roam keys for a struct.
type ScanResult struct {
	ClassName string
	RoamKeys  []dto.RoamKeyMeta
}

// ScanPackage scans all Go files in the given directory for leaf structs and their roam key accesses.
// It returns a map of struct name -> []RoamKeyMeta.
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
		for _, file := range pkg.Files {
			fileResults := scanFile(file)
			results = append(results, fileResults...)
		}
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

// scanFile scans a single AST file for structs that have DoFlow/DoResult/DoNone methods.
func scanFile(file *ast.File) []ScanResult {
	// Collect all struct method declarations
	type methodInfo struct {
		recv     string // receiver type name
		funcDecl *ast.FuncDecl
	}

	var methods []methodInfo
	structFields := map[string]map[string]bool{} // structName -> set of field names

	for _, decl := range file.Decls {
		switch d := decl.(type) {
		case *ast.FuncDecl:
			if d.Recv != nil && len(d.Recv.List) > 0 {
				recvType := resolveRecvType(d.Recv.List[0].Type)
				if recvType != "" {
					methods = append(methods, methodInfo{recv: recvType, funcDecl: d})
				}
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
					structFields[ts.Name.Name] = fields
				}
			}
		}
	}

	// Group methods by receiver type
	targetMethods := map[string]string{"DoFlow": "", "DoResult": "", "DoNone": ""}
	structMethods := map[string][]*ast.FuncDecl{} // structName -> target methods
	allStructMethods := map[string]map[string]*ast.FuncDecl{} // structName -> methodName -> funcDecl

	for _, m := range methods {
		if _, isTarget := targetMethods[m.funcDecl.Name.Name]; isTarget {
			structMethods[m.recv] = append(structMethods[m.recv], m.funcDecl)
		}
		if allStructMethods[m.recv] == nil {
			allStructMethods[m.recv] = map[string]*ast.FuncDecl{}
		}
		allStructMethods[m.recv][m.funcDecl.Name.Name] = m.funcDecl
	}

	var results []ScanResult
	for structName, funcs := range structMethods {
		var metas []dto.RoamKeyMeta
		fields := structFields[structName]
		otherMethods := allStructMethods[structName]

		for _, fn := range funcs {
			// Determine roam parameter name
			roamParam := findRoamParam(fn)
			if roamParam == "" {
				continue
			}
			recvName := ""
			if fn.Recv != nil && len(fn.Recv.List) > 0 && len(fn.Recv.List[0].Names) > 0 {
				recvName = fn.Recv.List[0].Names[0].Name
			}

			visited := map[string]bool{}
			scanFuncBody(fn.Body, roamParam, recvName, fields, otherMethods, &metas, visited, 0)
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
	otherMethods map[string]*ast.FuncDecl,
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
				// Check for cross-method calls that pass roam
				checkCrossMethod(node, sel, roamParam, recvName, fields, otherMethods, metas, assignments, visited, depth)
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

func checkCrossMethod(
	call *ast.CallExpr,
	sel *ast.SelectorExpr,
	roamParam, recvName string,
	fields map[string]bool,
	otherMethods map[string]*ast.FuncDecl,
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
	passesRoam := false
	roamArgIdx := -1
	for i, arg := range call.Args {
		if isRoamRef(arg, roamParam, assignments) {
			passesRoam = true
			roamArgIdx = i
			break
		}
	}
	if !passesRoam {
		return
	}

	methodName := sel.Sel.Name
	if visited[methodName] {
		return
	}
	visited[methodName] = true

	fn, ok := otherMethods[methodName]
	if !ok || fn.Body == nil {
		return
	}

	// Find roam param name in target method
	targetRoamParam := ""
	if fn.Type.Params != nil {
		paramIdx := 0
		for _, param := range fn.Type.Params.List {
			for _, name := range param.Names {
				if paramIdx == roamArgIdx {
					targetRoamParam = name.Name
				}
				paramIdx++
			}
		}
	}
	if targetRoamParam == "" {
		return
	}

	targetRecvName := ""
	if fn.Recv != nil && len(fn.Recv.List) > 0 && len(fn.Recv.List[0].Names) > 0 {
		targetRecvName = fn.Recv.List[0].Names[0].Name
	}

	scanFuncBody(fn.Body, targetRoamParam, targetRecvName, fields, otherMethods, metas, visited, depth+1)
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
