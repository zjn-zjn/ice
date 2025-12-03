package com.ice.server.dao.model;

import com.ice.common.dto.IcePushHistoryDto;
import lombok.Data;

import java.util.Date;

@Data
public class IcePushHistory {
    private Long id;

    private Integer app;

    private Long iceId;

    private String reason;

    private String operator;

    private Date createAt;

    private String pushData;

    public IcePushHistoryDto toDto() {
        IcePushHistoryDto dto = new IcePushHistoryDto();
        dto.setId(this.id);
        dto.setApp(this.app);
        dto.setIceId(this.iceId);
        dto.setReason(this.reason);
        dto.setOperator(this.operator);
        dto.setCreateAt(this.createAt != null ? this.createAt.getTime() : null);
        dto.setPushData(this.pushData);
        return dto;
    }

    public static IcePushHistory fromDto(IcePushHistoryDto dto) {
        if (dto == null) return null;
        IcePushHistory history = new IcePushHistory();
        history.setId(dto.getId());
        history.setApp(dto.getApp());
        history.setIceId(dto.getIceId());
        history.setReason(dto.getReason());
        history.setOperator(dto.getOperator());
        history.setCreateAt(dto.getCreateAt() != null ? new Date(dto.getCreateAt()) : null);
        history.setPushData(dto.getPushData());
        return history;
    }
}