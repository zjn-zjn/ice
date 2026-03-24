package handler

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/waitmoon/ice-server/config"
	"github.com/waitmoon/ice-server/model"
)

type PublishHandler struct {
	config *config.Config
	client *http.Client
}

func NewPublishHandler(cfg *config.Config) *PublishHandler {
	return &PublishHandler{
		config: cfg,
		client: &http.Client{Timeout: 30 * time.Second},
	}
}

func (h *PublishHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/base/publish", WrapHandler(h.publish))
}

func (h *PublishHandler) publish(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	var req struct {
		JSON   string `json:"json"`
		Target string `json:"target"`
	}
	if err := ReadJSONBody(r, &req); err != nil {
		return nil, model.InputError("request body")
	}
	if req.JSON == "" || req.Target == "" {
		return nil, model.InputError("json and target required")
	}

	var targetURL string
	for _, t := range h.config.PublishTargets {
		if t.Name == req.Target {
			targetURL = t.URL
			break
		}
	}
	if targetURL == "" {
		return nil, model.InputError("unknown publish target: " + req.Target)
	}

	targetURL = strings.TrimRight(targetURL, "/") + "/ice-server/base/import"
	body, _ := json.Marshal(map[string]string{"json": req.JSON})

	resp, err := h.client.Post(targetURL, "application/json", strings.NewReader(string(body)))
	if err != nil {
		slog.Error("publish request failed", "target", req.Target, "error", err)
		return nil, model.InternalError("publish failed: " + err.Error())
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)
	var result model.WebResult
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, model.InternalError("invalid response from target")
	}
	if result.Ret != 0 {
		return nil, model.CustomError("target: " + result.Msg)
	}
	return nil, nil
}
