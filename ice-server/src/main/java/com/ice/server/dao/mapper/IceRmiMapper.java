package com.ice.server.dao.mapper;

import com.ice.server.dao.model.IceRmi;
import com.ice.server.dao.model.IceRmiExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IceRmiMapper {
    long countByExample(IceRmiExample example);

    int deleteByExample(IceRmiExample example);

    int deleteByPrimaryKey(Long id);

    int insert(IceRmi record);

    int insertSelective(IceRmi record);

    List<IceRmi> selectByExample(IceRmiExample example);

    IceRmi selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") IceRmi record, @Param("example") IceRmiExample example);

    int updateByExample(@Param("record") IceRmi record, @Param("example") IceRmiExample example);

    int updateByPrimaryKeySelective(IceRmi record);

    int updateByPrimaryKey(IceRmi record);
}