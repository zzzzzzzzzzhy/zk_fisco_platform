package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.PrizeBatchItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 奖金发放批次明细Mapper
 */
@Mapper
public interface PrizeBatchItemMapper extends BaseMapper<PrizeBatchItem> {
}
