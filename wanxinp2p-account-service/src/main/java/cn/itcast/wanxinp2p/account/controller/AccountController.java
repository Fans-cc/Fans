package cn.itcast.wanxinp2p.account.controller;

import cn.itcast.wanxinp2p.account.service.AccountService;
import cn.itcast.wanxinp2p.api.account.AccountApi;
import cn.itcast.wanxinp2p.api.account.model.AccountDTO;
import cn.itcast.wanxinp2p.api.account.model.AccountLoginDTO;
import cn.itcast.wanxinp2p.api.account.model.AccountRegisterDTO;
import cn.itcast.wanxinp2p.common.domain.RestResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(value = "统一账户服务api",tags = "account",description = "统一账户服务api")
public class AccountController implements AccountApi{

    @Autowired
    AccountService accountService;

    @ApiOperation("测试")
    @GetMapping("/hello")
    public String Hello(){
        return "AccountHello";
    }

    /**
     *
     * @param mobile
     * @return
     */
    @Override
    @ApiOperation("获取手机验证码")
    @ApiImplicitParam(name = "mobile" , value = "手机号" , dataType = "String")
    @GetMapping("/sms/{mobile}")
    public RestResponse getSMSCode(@PathVariable String mobile) {
        return accountService.getSMSCode(mobile);
    }

    /**
     *
     * @param mobile
     * @param key
     * @param code
     * @return
     */
    @Override
    @ApiOperation("校验手机号和验证码")
    @ApiImplicitParams({@ApiImplicitParam(name = "mobile" , value = "手机号" , required = true ,dataType = "String"),
            @ApiImplicitParam(name = "key" , value = "校验标识" , required = true ,dataType = "String"),
            @ApiImplicitParam(name = "code" , value = "验证码" , required = true ,dataType = "String")})
    @GetMapping("/mobiles/{mobile}/key/{key}/code/{code}")
    public RestResponse checkMobile(@PathVariable String mobile, @PathVariable String key, @PathVariable String code) {

        return RestResponse.success(accountService.ckeckMobile(mobile,key,code));

    }
    @ApiOperation("账户注册")
    @ApiImplicitParam(name = "accountRegisterDTO", value = "账户注册信息", required = true,
            dataType = "AccountRegisterDTO", paramType = "body")
    @PostMapping(value = "/l/accounts")
    @Override
    public RestResponse<AccountDTO> register(@RequestBody AccountRegisterDTO accountRegisterDTO) {
        return RestResponse.success(accountService.register(accountRegisterDTO));
    }

    @ApiOperation("账户登陆")
    @ApiImplicitParam(name = "accountLoginDTO", value = "账户登陆信息", required = true,
            dataType = "AccountLoginDTO", paramType = "body")
    @PostMapping(value = "/l/accounts/session")
    @Override
    public RestResponse<AccountDTO> login(@RequestBody AccountLoginDTO accountLoginDTO) {
        return RestResponse.success(accountService.login(accountLoginDTO));
    }

}
