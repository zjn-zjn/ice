package main

import "fmt"

// Error codes matching Java ErrorCode enum
const (
	CodeInternalError     = -1
	CodeInputError        = -2
	CodeIDNotExist        = -3
	CodeAlreadyExist      = -5
	CodeConfNotFound      = -6
	CodeConfigFieldIllegal = -14
	CodeCustom            = -255
)

type ErrorCodeError struct {
	Code int
	Msg  string
}

func (e *ErrorCodeError) Error() string {
	return e.Msg
}

func InternalError(params ...interface{}) *ErrorCodeError {
	msg := "内部错误"
	if len(params) > 0 {
		msg = fmt.Sprintf("%v", params[0])
	}
	return &ErrorCodeError{Code: CodeInternalError, Msg: msg}
}

func InputError(params ...interface{}) *ErrorCodeError {
	msg := "输入错误"
	if len(params) > 0 {
		msg = fmt.Sprintf("输入错误: %v", params[0])
	}
	return &ErrorCodeError{Code: CodeInputError, Msg: msg}
}

func IDNotExist(name string, id interface{}) *ErrorCodeError {
	return &ErrorCodeError{Code: CodeIDNotExist, Msg: fmt.Sprintf("%s:%v 不存在", name, id)}
}

func AlreadyExist(params ...interface{}) *ErrorCodeError {
	msg := "已存在"
	if len(params) > 0 {
		msg = fmt.Sprintf("%v 已存在", params[0])
	}
	return &ErrorCodeError{Code: CodeAlreadyExist, Msg: msg}
}

func ConfNotFound(app int, name string, id interface{}) *ErrorCodeError {
	return &ErrorCodeError{Code: CodeConfNotFound, Msg: fmt.Sprintf("app:%d %s:%v 配置未找到", app, name, id)}
}

func ConfigFieldIllegal(detail string) *ErrorCodeError {
	return &ErrorCodeError{Code: CodeConfigFieldIllegal, Msg: fmt.Sprintf("配置不合法: %s", detail)}
}

func CustomError(msg string) *ErrorCodeError {
	return &ErrorCodeError{Code: CodeCustom, Msg: msg}
}
