package com.ice.server.dao.model;

import com.ice.common.constant.Constant;
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

    //only in ice_conf_update
    private Long iceId;
    private Long confId;


    public Set<Long> getSonLongIds() {
        if (NodeTypeEnum.isRelation(this.getType()) && StringUtils.hasLength(this.getSonIds())) {
            //relation node
            String[] sonIdStrs = this.getSonIds().split(Constant.REGEX_COMMA);
            Set<Long> sonLongIds = new HashSet<>();
            for (String sonStr : sonIdStrs) {
                sonLongIds.add(Long.valueOf(sonStr));
            }
            return sonLongIds;
        }
        return null;
    }
}