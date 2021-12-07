package com.ice.common.codec;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.JavaBeanSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerialContext;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.util.TypeUtils;
import com.ice.common.enums.IceSerializerFeature;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author zjn
 */
public final class IceListSerializer implements ObjectSerializer {

    private static final IceListSerializer INSTANCE = new IceListSerializer();

    public static IceListSerializer getInstance() {
        return INSTANCE;
    }

    @Override
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
            throws IOException {
        boolean isCustomNumber = serializer.out.isEnabled(IceSerializerFeature.CUSTOM_NUMBER_SHOW.getMask()) || (
                (features & IceSerializerFeature.CUSTOM_NUMBER_SHOW.getMask()) != 0);

        SerializeWriter out = serializer.out;
        Type elementType = null;
        if (isCustomNumber) {
            elementType = TypeUtils.getCollectionItemType(fieldType);
        }
        if (object == null) {
            out.writeNull(SerializerFeature.WriteNullListAsEmpty);
            return;
        }
        List<?> list = (List<?>) object;
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }

        SerialContext context = serializer.getContext();
        serializer.setContext(context, object, fieldName, 0);
        try {
            ObjectSerializer itemSerializer;
            if (out.isEnabled(SerializerFeature.PrettyFormat)) {
                out.append('[');
                serializer.incrementIndent();

                int i = 0;
                for (Object item : list) {
                    if (i != 0) {
                        out.append(',');
                    }

                    serializer.println();
                    if (item != null) {
                        if (serializer.containsReference(item)) {
                            serializer.writeReference(item);
                        } else {
                            itemSerializer = serializer.getObjectWriter(item.getClass());
                            serializer.setContext(new SerialContext(context, object, fieldName, 0, 0));
                            itemSerializer.write(serializer, item, i, elementType, features);
                        }
                    } else {
                        serializer.out.writeNull();
                    }
                    i++;
                }
                serializer.decrementIdent();
                serializer.println();
                out.append(']');
                return;
            }
            out.append('[');
            for (int i = 0, size = list.size(); i < size; ++i) {
                Object item = list.get(i);
                if (i != 0) {
                    out.append(',');
                }
                if (item == null) {
                    out.append("null");
                } else {
                    Class<?> clazz = item.getClass();

                    if (clazz == Integer.class) {
                        out.writeInt((Integer) item);
                    } else if (clazz == Long.class) {
                        long val = (Long) item;
                        out.writeLong(val);
                        if (isCustomNumber) {
                            out.write('L');
                        }
                    } else {
                        if ((SerializerFeature.DisableCircularReferenceDetect.mask & features) == 0) {
                            serializer.setContext(new SerialContext(context, object, fieldName, 0, 0));
                            if (serializer.containsReference(item)) {
                                serializer.writeReference(item);
                            } else {
                                itemSerializer = serializer.getObjectWriter(item.getClass());
                                if ((SerializerFeature.WriteClassName.mask & features) != 0
                                        && itemSerializer instanceof JavaBeanSerializer) {
                                    JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) itemSerializer;
                                    javaBeanSerializer.writeNoneASM(serializer, item, i, elementType, features);
                                } else {
                                    itemSerializer.write(serializer, item, i, elementType, features);
                                }
                            }
                        } else {
                            itemSerializer = serializer.getObjectWriter(item.getClass());
                            itemSerializer.write(serializer, item, i, elementType, features);
                        }
                    }
                }
            }
            out.append(']');
        } finally {
            serializer.setContext(context);
        }
    }
}
