package com.ice.server.model;

import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceBaseDto;
import lombok.Data;

import java.util.Date;

@Data
public class IceBase {
    private Long id;

    private String name;

    private Integer app;

    private String scenes;

    private Byte status;

    private Long confId;

    private Byte timeType;

    private Date start;

    private Date end;

    private Byte debug;

    private Long priority;

    private Date createAt;

    private Date updateAt;

    public IceBaseDto toDto() {
        IceBaseDto dto = new IceBaseDto();
        dto.setId(this.id);
        dto.setName(this.name);
        dto.setApp(this.app);
        dto.setScenes(this.scenes);
        dto.setStatus(this.status);
        dto.setConfId(this.confId);
        dto.setTimeType(this.timeType);
        dto.setStart(this.start != null ? this.start.getTime() : null);
        dto.setEnd(this.end != null ? this.end.getTime() : null);
        dto.setDebug(this.debug);
        dto.setPriority(this.priority);
        dto.setCreateAt(this.createAt != null ? this.createAt.getTime() : null);
        dto.setUpdateAt(this.updateAt != null ? this.updateAt.getTime() : null);
        return dto;
    }

    public static IceBase fromDto(IceBaseDto dto) {
        if (dto == null) return null;
        IceBase base = new IceBase();
        base.setId(dto.getId());
        base.setName(dto.getName());
        base.setApp(dto.getApp());
        base.setScenes(dto.getScenes());
        base.setStatus(dto.getStatus());
        base.setConfId(dto.getConfId());
        base.setTimeType(dto.getTimeType());
        base.setStart(dto.getStart() != null ? new Date(dto.getStart()) : null);
        base.setEnd(dto.getEnd() != null ? new Date(dto.getEnd()) : null);
        base.setDebug(dto.getDebug());
        base.setPriority(dto.getPriority());
        base.setCreateAt(dto.getCreateAt() != null ? new Date(dto.getCreateAt()) : null);
        base.setUpdateAt(dto.getUpdateAt() != null ? new Date(dto.getUpdateAt()) : null);
        return base;
    }

    public boolean isOnline() {
        return status != null && status == IceStorageConstants.STATUS_ONLINE;
    }

    public boolean isDeleted() {
        return status != null && status == IceStorageConstants.STATUS_DELETED;
    }
}