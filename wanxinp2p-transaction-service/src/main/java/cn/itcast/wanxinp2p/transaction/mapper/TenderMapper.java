package cn.itcast.wanxinp2p.transaction.mapper;

import cn.itcast.wanxinp2p.transaction.entity.Tender;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

public interface TenderMapper extends BaseMapper<Tender> {
    /**
     * 根据标的id, 获取标的已投金额, 如果未投返回0.0
     * @param id
     * @return
     */
    @Select("select ifnull(sum(amount),0.0) from tender where project_id = #{id} and status = 1")
    List<BigDecimal> selectAmountInvestedByProjectId(Long id);

}
