package com.ice.common.codec;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.util.TypeUtils;
import com.ice.common.enums.IceSerializerFeature;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author zjn
 */
public final class IceByteShortCodec implements ObjectSerializer, ObjectDeserializer {

  private static final IceByteShortCodec INSTANCE = new IceByteShortCodec();

  public static IceByteShortCodec getInstance() {
    return INSTANCE;
  }

  @Override
  public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) {
    SerializeWriter out = serializer.out;

    Number value = (Number) object;

    if (value == null) {
      out.writeNull(SerializerFeature.WriteNullNumberAsZero);
      return;
    }

    if (object instanceof Long) {
      out.writeLong(value.longValue());
    } else {
      out.writeInt(value.intValue());
    }
    if (out.isEnabled(IceSerializerFeature.CUSTOM_NUMBER_SHOW.getMask())) {
      Class<?> clazz = value.getClass();
      if (clazz == Byte.class) {
        out.write('B');
      } else if (clazz == Short.class) {
        out.write('S');
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
    final JSONLexer lexer = parser.lexer;

    final int token = lexer.token();

    if (token == JSONToken.NULL) {
      lexer.nextToken(JSONToken.COMMA);
      return null;
    }

    Integer intObj;
    try {
      if (token == JSONToken.LITERAL_INT) {
        int val = lexer.intValue();
        lexer.nextToken(JSONToken.COMMA);
        intObj = val;
      } else if (token == JSONToken.LITERAL_FLOAT) {
        BigDecimal number = lexer.decimalValue();
        intObj = TypeUtils.intValue(number);
        lexer.nextToken(JSONToken.COMMA);
      } else {
        if (token == JSONToken.LBRACE) {
          JSONObject jsonObject = new JSONObject(true);
          parser.parseObject(jsonObject);
          intObj = TypeUtils.castToInt(jsonObject);
        } else {
          Object value = parser.parse();
          intObj = TypeUtils.castToInt(value);
        }
      }
    } catch (Exception ex) {
      String message = "parseByteOrShort error";
      if (fieldName != null) {
        message += (", field : " + fieldName);
      }
      throw new JSONException(message, ex);
    }

    if (clazz == AtomicInteger.class) {
      return (T) new AtomicInteger(intObj);
    }

    return (T) intObj;
  }

  @Override
  public int getFastMatchToken() {
    return JSONToken.LITERAL_INT;
  }
}
