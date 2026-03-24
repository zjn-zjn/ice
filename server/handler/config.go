package handler

import (
	"net/http"

	"github.com/waitmoon/ice-server/config"
)

type ConfigHandler struct {
	config *config.Config
}

func NewConfigHandler(cfg *config.Config) *ConfigHandler {
	return &ConfigHandler{config: cfg}
}

func (h *ConfigHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/config/info", WrapHandler(h.info))
}

func (h *ConfigHandler) info(w http.ResponseWriter, r *http.Request) (any, error) {
	result := map[string]any{
		"mode": h.config.Mode,
	}
	if len(h.config.PublishTargets) > 0 {
		result["publishTargets"] = h.config.PublishTargets
	}
	return result, nil
}
