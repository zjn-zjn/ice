package client

import (
	"github.com/waitmoon/ice/sdks/go/cache"
	"github.com/waitmoon/ice/sdks/go/dto"
)

// Update updates the local cache from a TransferDto.
func Update(info *dto.TransferDto) []string {
	var errors []string

	// Delete confs first
	if len(info.DeleteConfIds) > 0 {
		cache.DeleteConfs(info.DeleteConfIds)
	}

	// Insert or update confs
	if len(info.InsertOrUpdateConfs) > 0 {
		errs := cache.InsertOrUpdateConfs(info.InsertOrUpdateConfs)
		errors = append(errors, errs...)
	}

	// Delete handlers
	if len(info.DeleteBaseIds) > 0 {
		cache.DeleteHandlers(info.DeleteBaseIds)
	}

	// Insert or update handlers
	if len(info.InsertOrUpdateBases) > 0 {
		errs := cache.InsertOrUpdateHandlers(info.InsertOrUpdateBases)
		errors = append(errors, errs...)
	}

	return errors
}
