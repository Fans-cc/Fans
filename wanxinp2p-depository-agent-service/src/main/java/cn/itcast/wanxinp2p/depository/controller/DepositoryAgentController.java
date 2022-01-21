package cn.itcast.wanxinp2p.depository.controller;

import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRequest;
import cn.itcast.wanxinp2p.api.depository.DepositoryAgentApi;
import cn.itcast.wanxinp2p.api.depository.model.*;
import cn.itcast.wanxinp2p.api.transaction.model.ModifyProjectStatusDTO;
import cn.itcast.wanxinp2p.api.transaction.model.ProjectDTO;
import cn.itcast.wanxinp2p.common.domain.RestResponse;
import cn.itcast.wanxinp2p.depository.service.DepositoryRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Api(value = "存管代理服务", tags = "depository-agent")
@RestController
public class DepositoryAgentController implements DepositoryAgentApi {
    @Autowired
    DepositoryRecordService depositoryRecordService;
    @Override
    @ApiOperation("生成开户请求数据")
    @ApiImplicitParam(name = "consumerRequest", value = "开户信息", required = true,
            dataType = "ConsumerRequest", paramType = "body")
    @PostMapping("/l/consumers")
    public RestResponse<GatewayRequest> createConsumer(@RequestBody ConsumerRequest consumerRequest) {
        return RestResponse.success(depositoryRecordService.createConsumer(consumerRequest));
    }

    @Override
    @ApiOperation("向银行存管系统发送标的信息")
    @ApiImplicitParam(name = "projectDTO", value = "向银行存管系统发送标的信息", required = true,
            dataType = "ProjectDTO", paramType = "body")
    @PostMapping("/l/createProject")
    public RestResponse<String> createProject(@RequestBody ProjectDTO projectDTO) {
        DepositoryResponseDTO<DepositoryBaseResponse> responseDTO = depositoryRecordService.createProject(projectDTO);
        RestResponse<String> restResponse = new RestResponse<>();
        restResponse.setResult(responseDTO.getRespData().getRespCode());
        restResponse.setMsg(responseDTO.getRespData().getRespMsg());
        return restResponse;
    }

    @Override
    @ApiOperation("预授权处理信息")
    @ApiImplicitParam(name = "userAutoPreTransactionRequest", value = "预授权处理信息", required = true,
            dataType = "UserAutoPreTransactionRequest", paramType = "body")
    @PostMapping("/l/user-auto-pre-transaction")
    public RestResponse<String> userAutoPreTransaction(@RequestBody UserAutoPreTransactionRequest userAutoPreTransactionRequest) {
        return getRestResponse(depositoryRecordService.userAutoPreTransaction(userAutoPreTransactionRequest));
    }

    @Override
    @ApiOperation(value = "审核标的满标放款")
    @ApiImplicitParam(name = "loanRequest", value = "标的满标放款信息", required = true, dataType = "LoanRequest", paramType = "body")
    @PostMapping("l/confirm-loan")
    public RestResponse<String> confirmLoan(LoanRequest loanRequest) {
        return getRestResponse(depositoryRecordService.confirmLoan(loanRequest));
    }

    @Override
    @ApiOperation(value = "修改标的状态")
    @ApiImplicitParam(name = "modifyProjectStatusDTO", value = "修改标的状态DTO", required = true, dataType = "ModifyProjectStatusDTO", paramType = "body")
    @PostMapping("l/modify-project-status")
    public RestResponse<String> modifyProjectStatus(ModifyProjectStatusDTO modifyProjectStatusDTO) {
        return getRestResponse(depositoryRecordService.modifyProjectStatus(modifyProjectStatusDTO));
    }

    @Override
    @ApiOperation(value = "确认还款")
    @ApiImplicitParam(name = "repaymentRequest", value = "还款信息", required = true, dataType = "RepaymentRequest", paramType = "body")
    @PostMapping("l/confirm-repayment")
    public RestResponse<String> confirmRepayment(RepaymentRequest repaymentRequest) {
        return getRestResponse(depositoryRecordService.confirmRepayment(repaymentRequest));
    }

    private RestResponse<String> getRestResponse(DepositoryResponseDTO<DepositoryBaseResponse> responseDTO){
        RestResponse<String> restResponse = new RestResponse<>();
        restResponse.setResult(responseDTO.getRespData().getRespCode());
        restResponse.setMsg(responseDTO.getRespData().getRespMsg());
        return restResponse;
    }
}
