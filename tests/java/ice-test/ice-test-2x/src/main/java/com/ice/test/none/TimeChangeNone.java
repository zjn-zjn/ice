package com.ice.test.none;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.base.BaseLeafNone;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.ZoneId;


/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public final class TimeChangeNone extends BaseLeafNone {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;

    private long cursorMills;

    @Value("${environment}")
    private String environment;

    /*
     * 叶子节点处理
     *
     * @param pack 包裹
     */
    @Override
    protected void doNone(IceRoam roam) {
        if (!"prod".equals(environment)) {
            if (time != null) {
                roam.setTs(time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            } else {
                roam.setTs(roam.getTs() + cursorMills);
            }
        }
    }
}
