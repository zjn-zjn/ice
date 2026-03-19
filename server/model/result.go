package model

// WebResult is the unified API response wrapper
type WebResult struct {
	Ret  int         `json:"ret"`
	Msg  string      `json:"msg,omitempty"`
	Data interface{} `json:"data,omitempty"`
}

func SuccessResult(data interface{}) *WebResult {
	return &WebResult{Ret: 0, Data: data}
}

func FailResult(code int, msg string) *WebResult {
	return &WebResult{Ret: code, Msg: msg}
}

// PageResult is the paginated response wrapper
type PageResult struct {
	Total    int64       `json:"total"`
	PageNum  int         `json:"pageNum"`
	PageSize int         `json:"pageSize"`
	Pages    int         `json:"pages"`
	List     interface{} `json:"list"`
}

func NewPageResult(list interface{}, total int64, pageNum, pageSize int) *PageResult {
	pages := 0
	if pageSize > 0 {
		pages = int(total+int64(pageSize)-1) / pageSize
	}
	if pages < 0 {
		pages = 0
	}
	return &PageResult{
		Total:    total,
		PageNum:  pageNum,
		PageSize: pageSize,
		Pages:    pages,
		List:     list,
	}
}
