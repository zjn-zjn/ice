package com.ice.test.none;

import com.ice.core.context.IcePack;
import com.ice.core.leaf.pack.BaseLeafPackNone;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public final class TimeChangeNone extends BaseLeafPackNone {

    private Date time;

    private long cursorMills;

    @Value("${environment}")
    private String environment;

    /*
     * 叶子节点处理
     *
     * @param pack 包裹
     */
    @Override
    protected void doPackNone(IcePack pack) {
        if (!"prod".equals(environment)) {
            if (time != null) {
                pack.setRequestTime(time.getTime());
            } else {
                pack.setRequestTime(pack.getRequestTime() + cursorMills);
            }
        }
    }
}
