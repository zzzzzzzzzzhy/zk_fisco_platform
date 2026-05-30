package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.DisbursementBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发放批次 Mapper
 */
@Mapper
public interface DisbursementBatchMapper extends BaseMapper<DisbursementBatch> {
}
