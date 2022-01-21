package cn.itcast.wanxinp2p.consumer.service;

import cn.itcast.wanxinp2p.api.account.model.AccountRegisterDTO;
import cn.itcast.wanxinp2p.api.consumer.model.BankCardDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRegisterDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRequest;
import cn.itcast.wanxinp2p.api.depository.model.DepositoryConsumerResponse;
import cn.itcast.wanxinp2p.api.depository.model.GatewayRequest;
import cn.itcast.wanxinp2p.api.transaction.model.BorrowerDTO;
import cn.itcast.wanxinp2p.common.domain.*;
import cn.itcast.wanxinp2p.common.util.CodeNoUtil;
import cn.itcast.wanxinp2p.consumer.agent.AccountApiAgent;
import cn.itcast.wanxinp2p.consumer.agent.DepositoryAgentApiAgent;
import cn.itcast.wanxinp2p.consumer.common.ConsumerErrorCode;
import cn.itcast.wanxinp2p.consumer.entity.BankCard;
import cn.itcast.wanxinp2p.consumer.entity.Consumer;
import cn.itcast.wanxinp2p.consumer.mapper.ConsumerMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hmily.annotation.Hmily;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ConsumerServiceImpl extends ServiceImpl<ConsumerMapper, Consumer> implements  ConsumerService{

    @Autowired
    AccountApiAgent accountApiAgent;

    @Autowired
    BankCardService bankCardService;

    @Autowired
    DepositoryAgentApiAgent depositoryAgentApiAgent;

    @Override
    public Integer checkMobile(String Mobile) {
        return getConsumer(Mobile)!=null?1:0;
    }
    @Override
    public ConsumerDTO getConsumer(String Mobile){
        ConsumerDTO consumerDTO = new ConsumerDTO();
        Consumer consumer = getOne(new QueryWrapper<Consumer>().lambda().eq(Consumer::getMobile, Mobile));
        if (consumer != null){
            BeanUtils.copyProperties(consumer,consumerDTO);
            return consumerDTO;
        }else {
            return null;
        }

    }

    @Override
    public BorrowerDTO getBorrower(Long id) {
        Consumer consumer = getById(id);
        return ConvertConsumerToBorrowerDTO(consumer);
    }

    /**
     * 用户注册
     * @param consumerRegisterDTO
     */
    @Override
    @Hmily(confirmMethod = "confirmRegister" , cancelMethod = "cancelRegister")
    public void register(ConsumerRegisterDTO consumerRegisterDTO) {
        //先验证手机号是否存在
        Integer integer = checkMobile(consumerRegisterDTO.getMobile());
        if (integer == 1){
            throw new BusinessException(ConsumerErrorCode.E_140107);
        }
        //手机号不存在进行用户注册
        Consumer consumer = new Consumer();
        if (consumerRegisterDTO != null ){
            BeanUtils.copyProperties(consumerRegisterDTO,consumer);
        }

        //随机生成用户名
        consumer.setUsername(CodeNoUtil.getNo(CodePrefixCode.CODE_NO_PREFIX));
        consumerRegisterDTO.setUsername(consumer.getUsername());
        //随机生成用户编码
        consumer.setUserNo(CodeNoUtil.getNo(CodePrefixCode.CODE_CONSUMER_PREFIX));
        consumer.setIsBindCard(0);
        save(consumer);

        //实现账户注册
        AccountRegisterDTO accountRegisterDTO = new AccountRegisterDTO();

        if (consumerRegisterDTO != null){
            BeanUtils.copyProperties(consumerRegisterDTO,accountRegisterDTO);
        }

        RestResponse restResponse = accountApiAgent.register(accountRegisterDTO);
        if (restResponse.getCode()!= CommonErrorCode.SUCCESS.getCode()||restResponse.getResult().toString().equalsIgnoreCase("false")){
            throw new BusinessException(ConsumerErrorCode.E_140106);
        }



    }

    @Override
    @Transactional
    public RestResponse<GatewayRequest> createConsumer(ConsumerRequest consumerRequest) {
        //1. 取出当前手机号所注册的用户
        ConsumerDTO consumerDTO = getConsumer(consumerRequest.getMobile());
        //2. 判断是否已经开户
        if (consumerDTO.getIsBindCard() == 1){
            //1 代表已经开户
            throw new BusinessException(ConsumerErrorCode.E_140105);
        }
        //3. 判断银行卡是否存在并且已经启用
        BankCardDTO bankCardDTO = bankCardService.getByCardNumber(consumerDTO.getIdNumber());
        if (bankCardDTO != null && bankCardDTO.getStatus() == StatusCode.STATUS_IN.getCode()){
            throw new BusinessException(ConsumerErrorCode.E_140151);
        }
        //4. 更新用户信息
        consumerRequest.setId(consumerDTO.getId());
        // 4.1 生成用户编号和请求编号
        consumerRequest.setUserNo(CodeNoUtil.getNo(CodePrefixCode.CODE_CONSUMER_PREFIX));
        consumerRequest.setRequestNo(CodeNoUtil.getNo(CodePrefixCode.CODE_REQUEST_PREFIX));
        UpdateWrapper<Consumer> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().eq(Consumer::getMobile,consumerDTO.getMobile());
        updateWrapper.lambda().set(Consumer::getFullname,consumerRequest.getFullname());
        updateWrapper.lambda().set(Consumer::getIdNumber,consumerRequest.getIdNumber());
        updateWrapper.lambda().set(Consumer::getAuthList,"ALL");
        updateWrapper.lambda().set(Consumer::getRequestNo,consumerRequest.getRequestNo());
        updateWrapper.lambda().set(Consumer::getUserNo,consumerRequest.getUserNo());
        update(updateWrapper);

        //5.更新用户绑卡信息
        BankCard bankCard = new BankCard();
        bankCard.setConsumerId(consumerDTO.getId());
        bankCard.setBankCode(consumerRequest.getBankCode());
        bankCard.setCardNumber(consumerRequest.getCardNumber());
        bankCard.setMobile(consumerRequest.getMobile());
        bankCard.setStatus(StatusCode.STATUS_OUT.getCode());
        // 判断该用户是否已经有绑卡信息，有的话更新没有的话新增
        BankCardDTO cardDTO = bankCardService.getByConsumerId(consumerDTO.getId());
        if (cardDTO != null){
            bankCard.setId(cardDTO.getId());
        }
        bankCardService.saveOrUpdate(bankCard);

        return depositoryAgentApiAgent.createConsumer(consumerRequest);
    }

    @Override
    @Transactional
    public Boolean modifyResult(DepositoryConsumerResponse response) {
        //1.获取状态
        int status = DepositoryReturnCode.RETURN_CODE_00000.getCode()
                .equals(response.getRespCode())
                ? StatusCode.STATUS_IN.getCode()
                : StatusCode.STATUS_FAIL.getCode();
        //2.更新开户结果
        Consumer consumer = getOne(new QueryWrapper<Consumer>().lambda().eq(Consumer::getRequestNo, response.getRequestNo()));
        boolean updateConsumer = update(Wrappers.<Consumer>lambdaUpdate().eq(Consumer::getId, consumer.getId())
                .set(Consumer::getIsBindCard, status).set(Consumer::getStatus, status));
        //3.更新银行卡信息
        boolean updateBankCard = bankCardService.update(Wrappers.<BankCard>lambdaUpdate().eq(BankCard::getConsumerId, consumer.getId())
                .set(BankCard::getStatus, status)
                .set(BankCard::getBankCode, response.getBankCode())
                .set(BankCard::getBankName, response.getBankName()));

        return updateConsumer && updateBankCard;
    }

    public void confirmRegister(ConsumerRegisterDTO consumerRegisterDTO){
        log.info("execute confirmRegister");
    }

    public void cancelRegister(ConsumerRegisterDTO consumerRegisterDTO){
        log.info("execute cancelRegister");
        remove(Wrappers.<Consumer>lambdaQuery().eq(Consumer::getMobile,consumerRegisterDTO.getMobile()));
    }

    private BorrowerDTO ConvertConsumerToBorrowerDTO(Consumer consumer){
        if (consumer == null){
            return null;
        }
        BorrowerDTO borrowerDTO = new BorrowerDTO();
        BeanUtils.copyProperties(consumer,borrowerDTO);
        return borrowerDTO;
    }
}
