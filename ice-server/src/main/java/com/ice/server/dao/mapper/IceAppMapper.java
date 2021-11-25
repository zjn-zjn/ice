package com.ice.server.dao.mapper;

import com.ice.server.dao.model.IceApp;
import com.ice.server.dao.model.IceAppExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface IceAppMapper {
    long countByExample(IceAppExample example);

    int deleteByExample(IceAppExample example);

    int deleteByPrimaryKey(Long id);

    int insert(IceApp record);

    int insertSelective(IceApp record);

    List<IceApp> selectByExample(IceAppExample example);

    IceApp selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") IceApp record, @Param("example") IceAppExample example);

    int updateByExample(@Param("record") IceApp record, @Param("example") IceAppExample example);

    int updateByPrimaryKeySelective(IceApp record);

    int updateByPrimaryKey(IceApp record);
}