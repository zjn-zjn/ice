package com.ice.core.utils;

import com.ice.common.enums.TimeSuffixEnum;
import com.ice.common.enums.TimeTypeEnum;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;

/**
 * @author zjn
 * Ice时间相关操作
 */
public final class IceTimeUtils {

  private IceTimeUtils() {
  }

  public static String combineSuffix(String origin, byte suffixType, LocalDateTime dateTime) {
    TimeSuffixEnum suffixEnum = TimeSuffixEnum.getEnum(suffixType);
    if (suffixEnum == null || suffixEnum == TimeSuffixEnum.NONE) {
      return origin;
    }
    return combineSuffix(origin, suffixEnum, dateTime);
  }

  public static String combineSuffix(String origin, byte suffixType, long timeMills) {
    TimeSuffixEnum suffixEnum = TimeSuffixEnum.getEnum(suffixType);
    if (suffixEnum == null || suffixEnum == TimeSuffixEnum.NONE) {
      return origin;
    }
    return combineSuffix(origin, suffixEnum,
        Instant.ofEpochMilli(timeMills).atZone(ZoneOffset.ofHours(8)).toLocalDateTime());
  }

  public static String combineSuffix(String origin, TimeSuffixEnum suffixEnum, LocalDateTime dateTime) {
    String suffix = getSuffix(suffixEnum, dateTime);
    if (suffix == null || suffix.isEmpty()) {
      return origin;
    }
    return origin + "_" + suffix;
  }

  public static String getSuffix(TimeSuffixEnum suffixEnum, LocalDateTime dateTime) {
    switch (suffixEnum) {
      case NONE:
        return "";
      case SECONDS:
        return String
            .format("%4d%02d%02d%02d%02d%02d", dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth(),
                dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond());
      case MINUTES:
        return String
            .format("%4d%02d%02d%02d%02d", dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth(),
                dateTime.getHour(), dateTime.getMinute());
      case HOURS:
        return String.format("%4d%02d%02d%02d", dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth(),
            dateTime.getHour());
      case DAYS:
        return String.format("%4d%02d%02d", dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth());
      case WEEKS:
        dateTime = dateTime.with(DayOfWeek.MONDAY);
        TemporalField weekBasedYear = WeekFields.of(DayOfWeek.MONDAY, 7).weekOfWeekBasedYear();
        return String.format("%4d%02d", dateTime.getYear(), dateTime.get(weekBasedYear));
      case MONTH:
        return String.format("%4d%02d", dateTime.getYear(), dateTime.getMonthValue());
      case YEAR:
        return String.format("%4d", dateTime.getYear());
      default:
        return null;
    }
  }

/*
   * 时间戳校验
   * 闭区间
   * @return time enable
   */
  public static boolean timeEnable(TimeTypeEnum typeEnum, long requestTime, long start, long end) {
    if (typeEnum == null) {
      return true;
    }
    switch (typeEnum) {
      case NONE:
        return false;
      case BETWEEN:
        return requestTime < start || requestTime > end;
      case AFTER_START:
        return requestTime < start;
      case BEFORE_END:
        return requestTime > end;
      default:
        break;
    }
    return true;
  }
}
