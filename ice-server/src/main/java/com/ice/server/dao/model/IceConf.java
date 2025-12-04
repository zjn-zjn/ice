package com.ice.server.dao.model;

import com.ice.common.constant.Constant;
import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceConfDto;
import com.ice.common.enums.NodeTypeEnum;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Data
public class IceConf {
    private Long id;

    private Integer app;

    private String name;

    private String sonIds;

    private Byte type;

    private Byte status;

    private Byte inverse;

    private String confName;

    private String confField;

    private Long forwardId;

    private Byte timeType;

    private Date start;

    private Date end;

    private Byte debug;

    private Byte errorState;

    private Date createAt;

    private Date updateAt;

    //only in ice_conf_update
    private Long iceId;
    private Long confId;

    //mix from update/conf
    public Long getMixId() {
        if (confId != null) {
            return confId;
        }
        return id;
    }

    public boolean isUpdatingConf() {
        return confId != null;
    }

    public Set<Long> getSonLongIds() {
        if (NodeTypeEnum.isRelation(this.getType()) && StringUtils.hasLength(this.getSonIds())) {
            String[] sonIdStrs = this.getSonIds().split(Constant.REGEX_COMMA);
            Set<Long> sonLongIds = new HashSet<>();
            for (String sonStr : sonIdStrs) {
                sonLongIds.add(Long.valueOf(sonStr));
            }
            return sonLongIds;
        }
        return null;
    }

    public boolean isOnline() {
        return status != null && status == IceStorageConstants.STATUS_ONLINE;
    }

    public boolean isDeleted() {
        return status != null && status == IceStorageConstants.STATUS_DELETED;
    }

    public IceConfDto toDto() {
        IceConfDto dto = new IceConfDto();
        dto.setId(this.getMixId());
        dto.setApp(this.app);
        dto.setName(this.name);
        dto.setSonIds(this.sonIds);
        dto.setType(this.type);
        dto.setStatus(this.status);
        dto.setInverse(this.inverse != null && this.inverse == 1);
        dto.setConfName(this.confName);
        dto.setConfField(this.confField);
        dto.setForwardId(this.forwardId);
        dto.setTimeType(this.timeType);
        dto.setStart(this.start != null ? this.start.getTime() : null);
        dto.setEnd(this.end != null ? this.end.getTime() : null);
        dto.setDebug(this.debug);
        dto.setErrorState(this.errorState);
        dto.setCreateAt(this.createAt != null ? this.createAt.getTime() : null);
        dto.setUpdateAt(this.updateAt != null ? this.updateAt.getTime() : null);
        dto.setIceId(this.iceId);
        dto.setConfId(this.confId);
        return dto;
    }

    public static IceConf fromDto(IceConfDto dto) {
        if (dto == null) return null;
        IceConf conf = new IceConf();
        conf.setId(dto.getId());
        conf.setApp(dto.getApp());
        conf.setName(dto.getName());
        conf.setSonIds(dto.getSonIds());
        conf.setType(dto.getType());
        conf.setStatus(dto.getStatus());
        conf.setInverse(dto.getInverse() != null && dto.getInverse() ? (byte) 1 : (byte) 0);
        conf.setConfName(dto.getConfName());
        conf.setConfField(dto.getConfField());
        conf.setForwardId(dto.getForwardId());
        conf.setTimeType(dto.getTimeType());
        conf.setStart(dto.getStart() != null ? new Date(dto.getStart()) : null);
        conf.setEnd(dto.getEnd() != null ? new Date(dto.getEnd()) : null);
        conf.setDebug(dto.getDebug());
        conf.setErrorState(dto.getErrorState());
        conf.setCreateAt(dto.getCreateAt() != null ? new Date(dto.getCreateAt()) : null);
        conf.setUpdateAt(dto.getUpdateAt() != null ? new Date(dto.getUpdateAt()) : null);
        conf.setIceId(dto.getIceId());
        conf.setConfId(dto.getConfId());
        return conf;
    }
}