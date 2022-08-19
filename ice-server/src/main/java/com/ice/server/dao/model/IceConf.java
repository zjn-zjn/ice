package com.ice.server.dao.model;

import lombok.Data;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class IceConf {
    private Long id;

    private Integer app;

    private String name;

    private String sonIds;

    private String linkIds;

    private String unlinkIds;

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

    private Date createAt;

    private Date updateAt;

    private Map<Long, Integer> linkIdMap;

    private Map<Long, Integer> unlinkIdMap;

    public void initUpdateConfLinks() {
        linkIdMap = new HashMap<>();
        unlinkIdMap = new HashMap<>();
        if (linkIds != null) {
            String[] linkIdStrs = linkIds.split(",");
            for (String linkIdStr : linkIdStrs) {
                Long linkId = Long.valueOf(linkIdStr);
                Integer cnt = linkIdMap.get(linkId);
                cnt = cnt == null ? 1 : cnt + 1;
                linkIdMap.put(linkId, cnt);
            }
        }
        if (unlinkIds != null) {
            String[] unlinkIdStrs = unlinkIds.split(",");
            for (String unlinkIdStr : unlinkIdStrs) {
                Long unlinkId = Long.valueOf(unlinkIdStr);
                Integer cnt = unlinkIdMap.get(unlinkId);
                cnt = cnt == null ? 1 : cnt + 1;
                unlinkIdMap.put(unlinkId, cnt);
            }
        }
    }

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
}