package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.Submission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提交记录Mapper
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {
}
