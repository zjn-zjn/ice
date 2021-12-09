package com.ice.server.dao.mapper;

import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceBaseExample;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface IceBaseMapper {
    long countByExample(IceBaseExample example);

    int deleteByExample(IceBaseExample example);

    int deleteByPrimaryKey(Long id);

    int insert(IceBase record);

    int insertWithId(IceBase record);

    int insertSelective(IceBase record);

    int insertSelectiveWithId(IceBase record);

    List<IceBase> selectByExample(IceBaseExample example);

    IceBase selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") IceBase record, @Param("example") IceBaseExample example);

    int updateByExample(@Param("record") IceBase record, @Param("example") IceBaseExample example);

    int updateByPrimaryKeySelective(IceBase record);

    int updateByPrimaryKey(IceBase record);
}