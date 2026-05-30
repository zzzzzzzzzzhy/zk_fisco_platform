package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.ZkRankingProof;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ZkRankingProofMapper extends BaseMapper<ZkRankingProof> {

    @Select("SELECT * FROM zk_ranking_proofs WHERE competition_id = #{competitionId} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    ZkRankingProof findLatestByCompetitionId(@Param("competitionId") Long competitionId);
}
