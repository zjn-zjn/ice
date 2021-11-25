package com.ice.server.dao.mapper;

import com.ice.server.dao.model.IcePushHistory;
import com.ice.server.dao.model.IcePushHistoryExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface IcePushHistoryMapper {
    long countByExample(IcePushHistoryExample example);

    int deleteByExample(IcePushHistoryExample example);

    int deleteByPrimaryKey(Long id);

    int insert(IcePushHistory record);

    int insertSelective(IcePushHistory record);

    List<IcePushHistory> selectByExampleWithBLOBs(IcePushHistoryExample example);

    List<IcePushHistory> selectByExample(IcePushHistoryExample example);

    IcePushHistory selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") IcePushHistory record, @Param("example") IcePushHistoryExample example);

    int updateByExampleWithBLOBs(@Param("record") IcePushHistory record, @Param("example") IcePushHistoryExample example);

    int updateByExample(@Param("record") IcePushHistory record, @Param("example") IcePushHistoryExample example);

    int updateByPrimaryKeySelective(IcePushHistory record);

    int updateByPrimaryKeyWithBLOBs(IcePushHistory record);

    int updateByPrimaryKey(IcePushHistory record);
}