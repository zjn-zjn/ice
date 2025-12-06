package com.ice.core.builder;

import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.cache.IceHandlerCache;
import com.ice.core.handler.IceHandler;
import lombok.Data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author waitmoon
 */
@Data
public class IceBuilder {

    private IceHandler handler;

    public IceBuilder(BaseNode root) {
        this.handler = new IceHandler();
        this.handler.setScenes(new HashSet<>());
        this.handler.setTimeTypeEnum(TimeTypeEnum.NONE);
        this.handler.setRoot(root);
    }

    public static IceBuilder root(BaseNode root) {
        return new IceBuilder(root);
    }

    public static IceBuilder root(BaseBuilder builder) {
        return new IceBuilder(builder.build());
    }

    public IceBuilder scene(String... scene) {
        Set<String> originScene = handler.getScenes();
        if (originScene == null) {
            originScene = new HashSet<>();
        }
        originScene.addAll(Arrays.asList(scene));
        return this;
    }

    public IceBuilder start(long start) {
        this.handler.setStart(start);
        return this;
    }

    public IceBuilder end(long end) {
        this.handler.setEnd(end);
        return this;
    }

    public IceBuilder timeType(TimeTypeEnum typeEnum) {
        this.handler.setTimeTypeEnum(typeEnum);
        return this;
    }

    public IceBuilder debug(byte debug) {
        this.handler.setDebug(debug);
        return this;
    }

    public void register() {
        IceHandlerCache.onlineOrUpdateHandler(this.handler);
    }

    public void register(String... scene) {
        this.scene(scene);
        IceHandlerCache.onlineOrUpdateHandler(this.handler);
    }
}
