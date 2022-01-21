package cn.itcast.wanxinp2p.api.consumer;

import cn.itcast.wanxinp2p.api.consumer.model.BalanceDetailsDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRegisterDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRequest;
import cn.itcast.wanxinp2p.api.depository.model.GatewayRequest;
import cn.itcast.wanxinp2p.api.transaction.model.BorrowerDTO;
import cn.itcast.wanxinp2p.common.domain.RestResponse;

public interface ConsumerApi {
    /**
     * 用户注册
     * @param consumerRegisterDTO
     * @return
     */
    RestResponse register(ConsumerRegisterDTO consumerRegisterDTO);

    /**
     * 开通存管账户
     * @param consumerRequest 开户信息
     * @return
     */
    RestResponse<GatewayRequest> createConsumer(ConsumerRequest consumerRequest);

    /**
     * 获取当前登陆用户 （内部使用）
     * @return
     */
    RestResponse<ConsumerDTO> getCurrConsumer(String mobile);
    /**
     * 获取当前登陆用户 （外部调用）
     * @return
     */
    RestResponse<ConsumerDTO> getMyConsumer();

    /**
     * 获取借款人用户信息
     * @param id
     * @return
     */
    RestResponse<BorrowerDTO> getBorrower(Long id);

    /**
     * 获取借款人用户信息,微服务使用
     * @param id
     * @return
     */
    RestResponse<BorrowerDTO> getBorrowerMobile(Long id);

    /**
     *  获取用户余额信息
     * @param userNo
     * @return
     */
    RestResponse<BalanceDetailsDTO> getBalance(String userNo);

    /**
     * 获取当前登录用户余额信息
     * @return
     */
    RestResponse<BalanceDetailsDTO> getMyBalance();

}
