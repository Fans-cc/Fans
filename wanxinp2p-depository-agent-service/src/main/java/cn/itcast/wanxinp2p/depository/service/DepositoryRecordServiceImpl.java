package cn.itcast.wanxinp2p.depository.service;

import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRequest;
import cn.itcast.wanxinp2p.api.depository.model.*;
import cn.itcast.wanxinp2p.api.transaction.model.ModifyProjectStatusDTO;
import cn.itcast.wanxinp2p.api.transaction.model.ProjectDTO;
import cn.itcast.wanxinp2p.common.cache.Cache;
import cn.itcast.wanxinp2p.common.domain.BusinessException;
import cn.itcast.wanxinp2p.common.domain.PreprocessBusinessTypeCode;
import cn.itcast.wanxinp2p.common.domain.StatusCode;
import cn.itcast.wanxinp2p.common.util.EncryptUtil;
import cn.itcast.wanxinp2p.common.util.RSAUtil;
import cn.itcast.wanxinp2p.depository.common.constant.DepositoryErrorCode;
import cn.itcast.wanxinp2p.depository.entity.DepositoryRecord;
import cn.itcast.wanxinp2p.depository.mapper.DepositoryRecordMapper;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DepositoryRecordServiceImpl extends ServiceImpl<DepositoryRecordMapper, DepositoryRecord> implements DepositoryRecordService {

    @Autowired
    ConfigService configService;
    @Autowired
    OkHttpService okHttpService;
    @Autowired
    Cache cache;
    @Override
    public GatewayRequest createConsumer(ConsumerRequest consumerRequest) {
        // 保存交易记录
        saveDepositoryRecord(consumerRequest);
        //2. 签名数据并返回
        String jsonConsumer= JSON.toJSONString(consumerRequest).replaceAll(" ", "");
        String sign = RSAUtil.sign(jsonConsumer, configService.getP2pPrivateKey().replaceAll(" ", ""), "utf-8");
        GatewayRequest gatewayRequest = new GatewayRequest();
        gatewayRequest.setServiceName("PERSONAL_REGISTER");
        gatewayRequest.setPlatformNo(configService.getP2pCode());
        gatewayRequest.setReqData(EncryptUtil.encodeURL(EncryptUtil.encodeUTF8StringBase64(jsonConsumer)));
        gatewayRequest.setSignature(EncryptUtil.encodeURL(sign));
        gatewayRequest.setDepositoryUrl(configService.getDepositoryUrl()+"/gateway");
        return gatewayRequest;
    }

    @Override
    public Boolean modifyRequestStatus(String requestNo, Integer requestsStatus) {
        return update(Wrappers.<DepositoryRecord>lambdaUpdate()
                .eq(DepositoryRecord::getRequestNo,requestNo)
        .set(DepositoryRecord::getRequestStatus,requestsStatus)
        .set(DepositoryRecord::getConfirmDate,LocalDateTime.now()));
    }

    @Override
    public DepositoryResponseDTO<DepositoryBaseResponse> createProject(ProjectDTO projectDTO) {
        //1.保存交易记录
        DepositoryRecord depositoryRecord = new DepositoryRecord(projectDTO.getRequestNo(), DepositoryRequestTypeCode.CREATE.getCode(),
                "Project", projectDTO.getId());
        DepositoryResponseDTO<DepositoryBaseResponse> responseDTO = handleIdempotent(depositoryRecord);
        if (responseDTO != null){
            return responseDTO;
        }

        // 根据requestNo获取交易记录
        depositoryRecord = getEntityByRequestNo(projectDTO.getRequestNo());

        //2.签名数据
        //2.1 ProjectDTO对象转化为 projectRequestDataDTO
        ProjectRequestDataDTO projectRequestDataDTO = convertProjectDTOToProjectRequestDataDTO(projectDTO);
        //2.2 转换为JSON
        String jsonString = JSON.toJSONString(projectRequestDataDTO);
        //base64 编码
        String reqData = EncryptUtil.encodeUTF8StringBase64(jsonString);
        //3. 往银行存管系统发送数据(标的信息),根据结果修改状态并返回结果
        // url地址 发送哪些数据
        String url=configService.getDepositoryUrl()+"/service";

        return sendHttpGet("CREATE_PROJECT",url,reqData,depositoryRecord);

    }

    @Override
    public DepositoryResponseDTO<DepositoryBaseResponse> userAutoPreTransaction(UserAutoPreTransactionRequest userAutoPreTransactionRequest) {
        DepositoryRecord depositoryRecord = new DepositoryRecord(userAutoPreTransactionRequest.getRequestNo(),
                userAutoPreTransactionRequest.getBizType(),"UserAutoPreTransactionRequest",userAutoPreTransactionRequest.getId());
        //实现幂等性
        DepositoryResponseDTO<DepositoryBaseResponse> responseDTO = handleIdempotent(depositoryRecord);
        if (responseDTO != null){
            return responseDTO;
        }
        // 根据requestNo获取交易记录
        depositoryRecord = getEntityByRequestNo(userAutoPreTransactionRequest.getRequestNo());

        //2.签名数据

        //2.2 转换为JSON
        String jsonString = JSON.toJSONString(userAutoPreTransactionRequest);
        //base64 编码
        String reqData = EncryptUtil.encodeUTF8StringBase64(jsonString);
        //3. 往银行存管系统发送数据(标的信息),根据结果修改状态并返回结果
        // url地址 发送哪些数据
        String url=configService.getDepositoryUrl()+"/service";

        return sendHttpGet("USER_AUTO_PRE_TRANSACTION",url,reqData,depositoryRecord);
    }

    @Override
    public DepositoryResponseDTO<DepositoryBaseResponse> confirmLoan(LoanRequest loanRequest) {
        DepositoryRecord depositoryRecord = new DepositoryRecord(loanRequest.getRequestNo(),
                DepositoryRequestTypeCode.FULL_LOAN.getCode(),"LoanRequest",loanRequest.getId());
        //实现幂等性
        DepositoryResponseDTO<DepositoryBaseResponse> responseDTO = handleIdempotent(depositoryRecord);
        if (responseDTO != null){
            return responseDTO;
        }
        // 根据requestNo获取交易记录
        depositoryRecord = getEntityByRequestNo(loanRequest.getRequestNo());
        //2.签名数据

        //2.2 转换为JSON
        String jsonString = JSON.toJSONString(loanRequest);
        //base64 编码
        String reqData = EncryptUtil.encodeUTF8StringBase64(jsonString);
        //3. 往银行存管系统发送数据(标的信息),根据结果修改状态并返回结果
        // url地址 发送哪些数据
        String url=configService.getDepositoryUrl()+"/service";

        return sendHttpGet("CONFIRM_LOAN",url,reqData,depositoryRecord);

    }

    @Override
    public DepositoryResponseDTO<DepositoryBaseResponse> modifyProjectStatus(ModifyProjectStatusDTO modifyProjectStatusDTO) {
        DepositoryRecord depositoryRecord = new DepositoryRecord(modifyProjectStatusDTO.getRequestNo(),
                DepositoryRequestTypeCode.MODIFY_STATUS.getCode(),"Project",modifyProjectStatusDTO.getId());
        //实现幂等性
        DepositoryResponseDTO<DepositoryBaseResponse> responseDTO = handleIdempotent(depositoryRecord);
        if (responseDTO != null){
            return responseDTO;
        }
        // 根据requestNo获取交易记录
        depositoryRecord = getEntityByRequestNo(modifyProjectStatusDTO.getRequestNo());
        //2.签名数据

        //2.2 转换为JSON
        String jsonString = JSON.toJSONString(modifyProjectStatusDTO);
        //base64 编码
        String reqData = EncryptUtil.encodeUTF8StringBase64(jsonString);
        //3. 往银行存管系统发送数据(标的信息),根据结果修改状态并返回结果
        // url地址 发送哪些数据
        String url=configService.getDepositoryUrl()+"/service";

        return sendHttpGet("MODIFY_PROJECT",url,reqData,depositoryRecord);
    }

    @Override
    public DepositoryResponseDTO<DepositoryBaseResponse> confirmRepayment(RepaymentRequest repaymentRequest) {
        DepositoryRecord depositoryRecord = new DepositoryRecord(repaymentRequest.getRequestNo(),
                PreprocessBusinessTypeCode.REPAYMENT.getCode(),"Repayment",repaymentRequest.getId());
        //实现幂等性
        DepositoryResponseDTO<DepositoryBaseResponse> responseDTO = handleIdempotent(depositoryRecord);
        if (responseDTO != null){
            return responseDTO;
        }
        // 根据requestNo获取交易记录
        depositoryRecord = getEntityByRequestNo(repaymentRequest.getRequestNo());
        //2.签名数据

        //2.2 转换为JSON
        String jsonString = JSON.toJSONString(repaymentRequest);
        //base64 编码
        String reqData = EncryptUtil.encodeUTF8StringBase64(jsonString);
        //3. 往银行存管系统发送数据(标的信息),根据结果修改状态并返回结果
        // url地址 发送哪些数据
        String url=configService.getDepositoryUrl()+"/service";

        return sendHttpGet("CONFIRM_REPAYMENT",url,reqData,depositoryRecord);
    }

    private void saveDepositoryRecord(ConsumerRequest consumerRequest){
        DepositoryRecord depositoryRecord = new DepositoryRecord();
        depositoryRecord.setRequestNo(consumerRequest.getRequestNo());
        depositoryRecord.setRequestType(DepositoryRequestTypeCode
                .CONSUMER_CREATE.getCode());
        depositoryRecord.setObjectType("Consumer");
        depositoryRecord.setObjectId(consumerRequest.getId());
        depositoryRecord.setCreateDate(LocalDateTime.now());
        depositoryRecord.setRequestStatus(StatusCode.STATUS_OUT.getCode());
        save(depositoryRecord);
    }

    private DepositoryRecord saveDepositoryRecord(DepositoryRecord depositoryRecord){
        //设置请求时间
        depositoryRecord.setCreateDate(LocalDateTime.now());
        //设置数据同步状态
        depositoryRecord.setRequestStatus(StatusCode.STATUS_OUT.getCode());
        save(depositoryRecord);
        return depositoryRecord;
    }

    private ProjectRequestDataDTO convertProjectDTOToProjectRequestDataDTO(ProjectDTO projectDTO){
        if (projectDTO == null){
            return null;
        }
        ProjectRequestDataDTO projectRequestDataDTO = new ProjectRequestDataDTO();
        BeanUtils.copyProperties(projectDTO,projectRequestDataDTO);
        return projectRequestDataDTO;
    }

    private DepositoryResponseDTO<DepositoryBaseResponse> sendHttpGet( String serviceName,
                                                                       String url, String reqData,
                                                                       DepositoryRecord depositoryRecord){
        // 银行存管系统接收的4大参数: serviceName, platformNo, reqData, signature
        // signature会在okHttp拦截器(SignatureInterceptor)中处理
        // 平台编号
        String platformNo = configService.getP2pCode();
        // 发送请求, 获取结果, 如果检验签名失败, 拦截器会在结果中放入: "signature", "false"
        String responseBody = okHttpService.doSyncGet(url + "?serviceName=" + serviceName + "&platformNo=" + platformNo + "&reqData=" + reqData);
        DepositoryResponseDTO<DepositoryBaseResponse> depositoryResponse = JSON .parseObject(responseBody, new TypeReference<DepositoryResponseDTO<DepositoryBaseResponse>>() { });
        depositoryRecord.setResponseData(responseBody);
        // 响应后, 根据结果更新数据库( 进行签名判断 )
        // 判断签名(signature)是为 false, 如果是说明验签失败!
        if ("false" != depositoryResponse.getSignature()){
            //成功 - 设置数据同步状态
            depositoryRecord.setRequestStatus(StatusCode.STATUS_IN.getCode());
            //设置消息确认时间
            depositoryRecord.setConfirmDate(LocalDateTime.now());
            //更新数据量
            updateById(depositoryRecord);
        }else {
            //失败 - 设置数据同步状态
            depositoryRecord.setRequestStatus(StatusCode.STATUS_FAIL.getCode());
            //设置消息确认时间
            depositoryRecord.setConfirmDate(LocalDateTime.now());
            //更新数据量
            updateById(depositoryRecord);
            //抛出异常
            throw new BusinessException(DepositoryErrorCode.E_160101);
        }

        return depositoryResponse;

    }

    /**
     * 实现幂等性
     * 当存管代理服务被交易中心远程请求时，首先根据该请求的requestNo获取交易记录，然后分三种情况
     * 进行处理：
     * 1. 若交易记录不存在则新增交易记录，requestNo为唯一索引，新保存的数据的状态为“未同步”。
     * 2. 若交易记录存在并且数据状态为“未同步”，此阶段存在并发可能，利用redis原子性自增，来争夺请求执行权，若count大于1，说明已有线程在执行该操作，直接返回“正在处理”。
     * 3. 若交易记录存在并且数据状态为已同步，直接返回处理结果。
     * @param depositoryRecord
     * @return
     */
    private DepositoryResponseDTO<DepositoryBaseResponse> handleIdempotent(DepositoryRecord depositoryRecord){
        String requestNo = depositoryRecord.getRequestNo();
        //根据requestNo查询交易记录
        DepositoryRecordDTO depositoryRecordDTO = getByRequestNo(requestNo);
        //1.交易记录不存在
        if (depositoryRecordDTO == null){
            //保存交易记录
            saveDepositoryRecord(depositoryRecord);
            return null;
        }
        //2.若交易记录存在并且数据状态为“未同步”
        if (depositoryRecordDTO.getRequestStatus()==StatusCode.STATUS_OUT.getCode()){
            //如果requestNo不存在则返回1,如果已经存在,则会返回（requestNo已存在个数+1）
            Long count = cache.incrBy(requestNo, 1L);
            if (count == 1){
                cache.expire(requestNo, 5); //设置requestNo有效期5秒
                return null;
            }
            if (count > 1){
                throw new BusinessException(DepositoryErrorCode.E_160103);
            }
        }
        //3. 若交易记录存在并且数据状态为已同步，直接返回处理结果。
        return JSON.parseObject(depositoryRecordDTO.getResponseData(),new TypeReference<DepositoryResponseDTO<DepositoryBaseResponse>>(){});
    }
    private DepositoryRecordDTO getByRequestNo(String requestNo){
        DepositoryRecord depositoryRecord = getEntityByRequestNo(requestNo);
        if (depositoryRecord == null){
            return null;
        }
        DepositoryRecordDTO depositoryRecordDTO = new DepositoryRecordDTO();
        BeanUtils.copyProperties(depositoryRecord,depositoryRecordDTO);
        return depositoryRecordDTO;
    }

    private DepositoryRecord getEntityByRequestNo(String requestNo){
        return getOne(Wrappers.<DepositoryRecord>lambdaQuery().eq(DepositoryRecord::getRequestNo, requestNo));
    }
}
