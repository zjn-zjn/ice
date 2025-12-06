package com.ice.server.model;

import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceAppDto;
import lombok.Data;

import java.util.Date;

@Data
public class IceApp {
    private Integer id;

    private String name;

    private String info;

    private Byte status;

    private Date createAt;

    private Date updateAt;

    public IceAppDto toDto() {
        IceAppDto dto = new IceAppDto();
        dto.setId(this.id);
        dto.setName(this.name);
        dto.setInfo(this.info);
        dto.setStatus(this.status);
        dto.setCreateAt(this.createAt != null ? this.createAt.getTime() : null);
        dto.setUpdateAt(this.updateAt != null ? this.updateAt.getTime() : null);
        return dto;
    }

    public static IceApp fromDto(IceAppDto dto) {
        if (dto == null) return null;
        IceApp app = new IceApp();
        app.setId(dto.getId());
        app.setName(dto.getName());
        app.setInfo(dto.getInfo());
        app.setStatus(dto.getStatus());
        app.setCreateAt(dto.getCreateAt() != null ? new Date(dto.getCreateAt()) : null);
        app.setUpdateAt(dto.getUpdateAt() != null ? new Date(dto.getUpdateAt()) : null);
        return app;
    }

    public boolean isOnline() {
        return status != null && status == IceStorageConstants.STATUS_ONLINE;
    }
}