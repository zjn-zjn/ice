package com.ice.server.constant;

import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceConfDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.model.IceShowNode;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceConf;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

public final class Constant {

    /**
     * base convert to dto
     * some filed has default value so delete it to improve trans
     *
     * @param base base in db
     * @return dto
     */
    public static IceBaseDto baseToDto(IceBase base) {
        IceBaseDto dto = new IceBaseDto();
        dto.setConfId(base.getConfId());
        if (base.getDebug() != null && base.getDebug() != 0) {
            dto.setDebug(base.getDebug());
        }
        dto.setId(base.getId());
        dto.setStart(base.getStart() == null ? null : base.getStart().getTime());
        dto.setEnd(base.getEnd() == null ? null : base.getEnd().getTime());
        if (base.getTimeType() != null && base.getTimeType() != TimeTypeEnum.NONE.getType()) {
            dto.setTimeType(base.getTimeType());
        }
        if (StringUtils.hasLength(base.getScenes())) {
            dto.setScenes(base.getScenes());
        }
        return dto;
    }

    public static IceBaseDto baseToDtoWithName(IceBase base) {
        IceBaseDto dto = baseToDto(base);
        if (StringUtils.hasLength(base.getName())) {
            dto.setName(base.getName());
        }
        return dto;
    }

    /**
     * conf convert to dto
     * some filed has default value so delete it to improve trans
     *
     * @param conf base in db
     * @return dto
     */
    public static IceConfDto confToDto(IceConf conf) {
        IceConfDto dto = new IceConfDto();
        dto.setForwardId(conf.getForwardId());
        if (conf.getDebug() != null && conf.getDebug() != 1) {
            dto.setDebug(conf.getDebug());
        }
        dto.setId(conf.getId());
        dto.setStart(conf.getStart() == null ? null : conf.getStart().getTime());
        dto.setEnd(conf.getEnd() == null ? null : conf.getEnd().getTime());
        if (conf.getTimeType() != null && conf.getTimeType() != TimeTypeEnum.NONE.getType()) {
            dto.setTimeType(conf.getTimeType());
        }
        if (NodeTypeEnum.isLeaf(conf.getType())) {
            dto.setConfName(conf.getConfName());
            if (StringUtils.hasLength(conf.getConfField()) && !conf.getConfField().equals("{}")) {
                dto.setConfField(conf.getConfField());
            }
        } else if (StringUtils.hasLength(conf.getSonIds())) {
            dto.setSonIds(conf.getSonIds());
        }
        if (conf.getInverse() != null && conf.getInverse() != 0) {
            dto.setInverse(true);
        }
        dto.setType(conf.getType());
        return dto;
    }

    public static IceConfDto confToDtoWithName(IceConf conf) {
        IceConfDto dto = confToDto(conf);
        if (StringUtils.hasLength(conf.getName())) {
            dto.setName(conf.getName());
        }
        return dto;
    }

    public static IceBase dtoToBase(IceBaseDto dto, Integer app) {
        if (dto == null) {
            return null;
        }
        IceBase base = new IceBase();
        base.setApp(app);
        base.setName(dto.getName());
        base.setConfId(dto.getConfId());
        base.setDebug(dto.getDebug() == null ? 0 : dto.getDebug());
        base.setId(dto.getId());
        base.setStart(dto.getStart() == null ? null : new Date(dto.getStart()));
        base.setEnd(dto.getEnd() == null ? null : new Date(dto.getEnd()));
        base.setTimeType(dto.getTimeType() == null ? TimeTypeEnum.NONE.getType() : dto.getTimeType());
        base.setScenes(dto.getScenes());
        base.setStatus((byte) 1);
        return base;
    }

    public static IceConf dtoToConf(IceConfDto dto, Integer app) {
        IceConf conf = new IceConf();
        conf.setApp(app);
        conf.setName(dto.getName());
        conf.setForwardId(dto.getForwardId());
        conf.setDebug(dto.getDebug() == null ? 1 : dto.getDebug());
        conf.setId(dto.getId());
        conf.setStart(dto.getStart() == null ? null : new Date(dto.getStart()));
        conf.setEnd(dto.getEnd() == null ? null : new Date(dto.getEnd()));
        conf.setTimeType(dto.getTimeType() == null ? TimeTypeEnum.NONE.getType() : dto.getTimeType());
        if (NodeTypeEnum.isLeaf(dto.getType())) {
            conf.setConfName(dto.getConfName());
            conf.setConfField(StringUtils.isEmpty(dto.getConfField()) ? "" : dto.getConfField());
        } else if (StringUtils.hasLength(dto.getSonIds())) {
            conf.setSonIds(dto.getSonIds());
        }
        conf.setInverse(dto.getInverse() == null ? (byte) 0 : (dto.getInverse() ? (byte) 0 : (byte) 1));
        conf.setType(dto.getType());
        conf.setStatus((byte) 1);
        return conf;
    }

    public static IceShowNode confToShow(IceConf conf, boolean update) {
        IceShowNode show = new IceShowNode();
        show.setForwardId(conf.getForwardId());
        show.setDebug(conf.getDebug() == null || conf.getDebug() == 1);
        show.setId(conf.getId());
        show.setStart(conf.getStart() == null ? null : conf.getStart().getTime());
        show.setEnd(conf.getEnd() == null ? null : conf.getEnd().getTime());
        if (conf.getTimeType() != null && conf.getTimeType() != TimeTypeEnum.NONE.getType()) {
            show.setTimeType(conf.getTimeType());
        }
        if (NodeTypeEnum.isRelation(conf.getType())) {
            if (StringUtils.hasLength(conf.getSonIds())) {
                show.setSonIds(conf.getSonIds());
            }
            show.setLabelName(conf.getId() + (update ? "^" : "") + "-" + NodeTypeEnum.getEnum(conf.getType()).name() + (StringUtils.hasLength(conf.getConfName()) ? ("-" + conf.getName()) : ""));
        } else {
            show.setConfName(conf.getConfName());
            if (StringUtils.hasLength(conf.getConfField()) && !conf.getConfField().equals("{}")) {
                show.setConfField(conf.getConfField());
            }
            show.setLabelName(conf.getId() + (update ? "^" : "") + "-" + (StringUtils.hasLength(conf.getConfName()) ? conf.getConfName().substring(conf.getConfName().lastIndexOf('.') + 1) : " ") + (StringUtils.hasLength(conf.getConfName()) ? ("-" + conf.getName()) : ""));
        }
        show.setInverse(conf.getInverse() != null && conf.getInverse() == 1);
        show.setName(conf.getName());
        show.setNodeType(conf.getType());
        return show;
    }

    public static Collection<IceConfDto> confListToDtoList(Collection<IceConf> confList) {
        if (CollectionUtils.isEmpty(confList)) {
            return Collections.emptyList();
        }
        Collection<IceConfDto> results = new ArrayList<>(confList.size());
        for (IceConf conf : confList) {
            results.add(Constant.confToDto(conf));
        }
        return results;
    }

    public static Collection<IceConfDto> confListToDtoListWithName(Collection<IceConf> confList) {
        if (CollectionUtils.isEmpty(confList)) {
            return Collections.emptyList();
        }
        Collection<IceConfDto> results = new ArrayList<>(confList.size());
        for (IceConf conf : confList) {
            results.add(Constant.confToDtoWithName(conf));
        }
        return results;
    }

    public static Collection<IceBaseDto> baseListToDtoList(Collection<IceBase> baseList) {
        if (CollectionUtils.isEmpty(baseList)) {
            return Collections.emptyList();
        }
        Collection<IceBaseDto> results = new ArrayList<>(baseList.size());
        for (IceBase base : baseList) {
            results.add(Constant.baseToDto(base));
        }
        return results;
    }

    public static Collection<IceConf> dtoListToConfList(Collection<IceConfDto> dtoList, Integer app) {
        if (CollectionUtils.isEmpty(dtoList)) {
            return Collections.emptyList();
        }
        Collection<IceConf> results = new ArrayList<>(dtoList.size());
        for (IceConfDto dto : dtoList) {
            results.add(Constant.dtoToConf(dto, app));
        }
        return results;
    }
}
