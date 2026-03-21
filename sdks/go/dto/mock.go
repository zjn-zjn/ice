package dto

// MockRequest is the mock execution request from server to client.
type MockRequest struct {
	MockId   string         `json:"mockId"`
	App      int            `json:"app"`
	IceId    int64          `json:"iceId,omitempty"`
	ConfId   int64          `json:"confId,omitempty"`
	Scene    string         `json:"scene,omitempty"`
	Ts       int64          `json:"ts,omitempty"`
	Debug    byte           `json:"debug,omitempty"`
	Roam     map[string]any `json:"roam,omitempty"`
	CreateAt int64          `json:"createAt"`
}

// MockResult is the mock execution result from client to server.
type MockResult struct {
	MockId    string         `json:"mockId"`
	Success   bool           `json:"success"`
	Roam      map[string]any `json:"roam,omitempty"`
	Trace     string         `json:"trace,omitempty"`
	Ts        int64          `json:"ts,omitempty"`
	Process   string         `json:"process,omitempty"`
	Error     string         `json:"error,omitempty"`
	ExecuteAt int64          `json:"executeAt"`
}
