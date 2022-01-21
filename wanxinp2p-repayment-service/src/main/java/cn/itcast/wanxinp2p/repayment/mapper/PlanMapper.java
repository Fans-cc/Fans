package cn.itcast.wanxinp2p.repayment.mapper;

import cn.itcast.wanxinp2p.repayment.entity.RepaymentPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 借款人还款计划 Mapper 接口
 */
public interface PlanMapper extends BaseMapper<RepaymentPlan> {
    /**
     * 查询所有到期的还款计划
     * @param date
     * @return
     */
    @Select("SELECT * FROM repayment_plan WHERE DATE_FORMAT(SHOULD_REPAYMENT_DATE,'%Y-%m-%d') = '2021-11-11' AND REPAYMENT_STATUS = '0' AND MOD(NUMBER_OF_PERIODS,#{shardingCount}) = #{shardingItem} ")
    List<RepaymentPlan> selectDueRepayment (@Param("date") String date, @Param("shardingCount")int shardingCount, @Param("shardingItem")int shardingItem);

}
