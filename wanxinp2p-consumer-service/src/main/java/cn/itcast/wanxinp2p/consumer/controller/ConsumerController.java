package cn.itcast.wanxinp2p.consumer.controller;

import cn.itcast.wanxinp2p.api.consumer.ConsumerApi;
import cn.itcast.wanxinp2p.api.consumer.model.BalanceDetailsDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRegisterDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRequest;
import cn.itcast.wanxinp2p.api.depository.model.GatewayRequest;
import cn.itcast.wanxinp2p.api.transaction.model.BorrowerDTO;
import cn.itcast.wanxinp2p.common.domain.RestResponse;
import cn.itcast.wanxinp2p.consumer.common.SecurityUtil;
import cn.itcast.wanxinp2p.consumer.service.ConsumerService;
import com.alibaba.fastjson.JSON;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.log4j.Log4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Log4j
@RestController
@Api(value = "用户服务的Controller", tags = "Consumer", description = "用户服务API")
public class ConsumerController implements ConsumerApi {

    @Value("${depository.url}")
    private String depositoryURL;

    private OkHttpClient okHttpClient = new OkHttpClient().newBuilder().build();

    @Autowired
    ConsumerService consumerService;
    @ApiOperation("测试hello")
    @GetMapping(path = "/hello")
    public String hello(){
        return "hello";
    }

    @ApiOperation("测试hi")
    @PostMapping(path="/hi")
    @ApiImplicitParam(name="name",value = "姓名",required = true,dataType = "String")
    public String hi(String name){
        return "hi,"+name;
    }

    @Override
    @ApiOperation("用户注册")
    @ApiImplicitParam(name = "consumerRegisterDTO",value = "用户注册信息",
            required = true, dataType = "ConsumerRegisterDTO", paramType = "body")
    @PostMapping(value = "/consumers")
    public RestResponse register(@RequestBody ConsumerRegisterDTO consumerRegisterDTO) {
        consumerService.register(consumerRegisterDTO);
        return RestResponse.success();
    }

    @Override
    @ApiOperation("生成开户请求数据")
    @ApiImplicitParam(name = "consumerRequest",value = "开户信息",
            required = true, dataType = "ConsumerRequest", paramType = "body")
    @PostMapping(value = "/my/consumers")
    public RestResponse<GatewayRequest> createConsumer(@RequestBody ConsumerRequest consumerRequest) {
        consumerRequest.setMobile(SecurityUtil.getUser().getMobile());
        return consumerService.createConsumer(consumerRequest);
    }

    @Override
    @ApiOperation("获取登录用户信息")
    @GetMapping("/l/currConsumer/{mobile}")
    public RestResponse<ConsumerDTO> getCurrConsumer(@PathVariable("mobile") String mobile) {
        ConsumerDTO consumerDTO = consumerService.getConsumer(mobile);
        return RestResponse.success(consumerDTO);
    }

    @Override
    @ApiOperation("获取登录用户信息")
    @GetMapping("/my/consumers")
    public RestResponse<ConsumerDTO> getMyConsumer() {
        ConsumerDTO consumerDTO = consumerService.getConsumer(SecurityUtil.getUser().getMobile());
        return RestResponse.success(consumerDTO);
    }

    @Override
    @ApiOperation("获取借款人用户信息")
    @ApiImplicitParams({ @ApiImplicitParam(name = "id", value = "用户标识", required = true, dataType = "Long", paramType = "path")
    })
    @GetMapping("/my/borrowers/{id}")
    public RestResponse<BorrowerDTO> getBorrower(@PathVariable Long id) {
        return RestResponse.success(consumerService.getBorrower(id));
    }

    @Override
    @ApiOperation("获取借款人用户信息")
    @ApiImplicitParams({ @ApiImplicitParam(name = "id", value = "用户标识", required = true, dataType = "Long", paramType = "path")
    })
    @GetMapping("/l/borrowers/{id}")
    public RestResponse<BorrowerDTO> getBorrowerMobile(@PathVariable Long id) {
        return RestResponse.success(consumerService.getBorrower(id));
    }

    @Override
    @ApiOperation("获取用户可用余额")
    @ApiImplicitParam(name = "userNo", value = "用户编码", required = true, dataType = "String")
    @GetMapping("/l/balances/{userNo}")
    public RestResponse<BalanceDetailsDTO> getBalance(@PathVariable String userNo) {
        return getBalanceFromDepository(userNo);
    }

    @Override
    @ApiOperation("获取当前登录用户可用余额")
    @GetMapping("/my/balances")
    public RestResponse<BalanceDetailsDTO> getMyBalance() {
        ConsumerDTO consumerDTO = consumerService.getConsumer(SecurityUtil.getUser().getMobile());
        return getBalanceFromDepository(consumerDTO.getUserNo());
    }

    private RestResponse<BalanceDetailsDTO> getBalanceFromDepository(String userNo){
        String url = depositoryURL + "/balance-details/" + userNo;
        Request request = new Request.Builder().url(url).build();
        try(Response response= okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()){
                String responseBody = response.body().string();
                BalanceDetailsDTO balanceDetailsDTO = JSON.parseObject(responseBody, BalanceDetailsDTO.class);
                return RestResponse.success(balanceDetailsDTO);
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.warn("调用存管系统失败");
        }
        return RestResponse.validfail("获取失败");
    }

}
