package com.wereen.competitionplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wereen.competitionplatform.model.entity.SubmissionCommitment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SubmissionCommitmentMapper extends BaseMapper<SubmissionCommitment> {

    @Select("SELECT * FROM submission_commitments WHERE competition_id = #{competitionId} AND deleted = 0 ORDER BY id ASC")
    List<SubmissionCommitment> findByCompetitionId(@Param("competitionId") Long competitionId);

    @Select("SELECT * FROM submission_commitments WHERE competition_id = #{competitionId} AND user_id = #{userId} AND deleted = 0 LIMIT 1")
    SubmissionCommitment findByCompetitionAndUser(@Param("competitionId") Long competitionId,
                                                   @Param("userId") Long userId);
}
