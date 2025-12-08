package com.ice.core.context;

import com.ice.common.enums.RequestTypeEnum;
import com.ice.common.utils.UUIDUtils;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author waitmoon
 * process ice input
 */
@Data
@ToString
public final class IcePack implements Serializable {

    /*
     * process iceId (db-base_id)
     */
    private long iceId;
    /*
     * process scene
     */
    private String scene;
    /*
     * process from nodeId
     */
    private long confId;
    /*
     * business field in/out
     */
    private volatile IceRoam roam = new IceRoam();
    /*
     * @see RequestTypeEnum
     */
    private int type = RequestTypeEnum.FORMAL.getType();

    private long requestTime;

    private String traceId;

    private long priority;

    /*
     * 1.handler debug|handler.debug
     * 2.confRoot this.debug
     */
    private byte debug;

    public IcePack() {
        this.setTraceId(UUIDUtils.generateUUID22());
        this.requestTime = System.currentTimeMillis();
    }

    public IcePack(String traceId, long requestTime) {
        if (traceId == null || traceId.isEmpty()) {
            /*traceId null set default traceId*/
            this.setTraceId(UUIDUtils.generateUUID22());
        } else {
            this.traceId = traceId;
        }
        if (requestTime <= 0) {
            this.requestTime = System.currentTimeMillis();
        } else {
            this.requestTime = requestTime;
        }
    }

    public IcePack(long requestTime) {
        /*traceId null set default traceId*/
        this.setTraceId(UUIDUtils.generateUUID22());
        if (requestTime <= 0) {
            this.requestTime = System.currentTimeMillis();
        } else {
            this.requestTime = requestTime;
        }
    }

    public IcePack(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            /*traceId null set default traceId*/
            this.setTraceId(UUIDUtils.generateUUID22());
        } else {
            this.traceId = traceId;
        }
        this.requestTime = System.currentTimeMillis();
    }

    public IcePack newPack(IceRoam roam) {
        IcePack pack = new IcePack(traceId, requestTime);
        pack.setIceId(iceId);
        pack.setScene(scene);
        if (roam != null) {
            /*shallow copy*/
            pack.setRoam(new IceRoam(roam));
        }
        pack.setType(type);
        pack.setPriority(priority);
        return pack;
    }
}
