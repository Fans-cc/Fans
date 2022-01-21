package cn.itcast.wanxinp2p.api.repayment;

import cn.itcast.wanxinp2p.api.repayment.model.ProjectWithTendersDTO;
import cn.itcast.wanxinp2p.common.domain.RestResponse;

public interface RepaymentApi {

    RestResponse<String> startRepayment(ProjectWithTendersDTO projectWithTendersDTO);

}
