package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.Registration;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报名记录Mapper
 */
@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {
}
