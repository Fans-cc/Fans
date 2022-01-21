package cn.itcast.wanxinp2p.account.service;

import cn.itcast.wanxinp2p.account.common.AccountErrorCode;
import cn.itcast.wanxinp2p.account.entity.Account;
import cn.itcast.wanxinp2p.account.mapper.AccountMapper;
import cn.itcast.wanxinp2p.api.account.model.AccountDTO;
import cn.itcast.wanxinp2p.api.account.model.AccountLoginDTO;
import cn.itcast.wanxinp2p.api.account.model.AccountRegisterDTO;
import cn.itcast.wanxinp2p.common.domain.BusinessException;
import cn.itcast.wanxinp2p.common.domain.RestResponse;
import cn.itcast.wanxinp2p.common.util.PasswordUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hmily.annotation.Hmily;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccountServiceImpl extends ServiceImpl<AccountMapper,Account>implements AccountService {
    @Autowired
    SmsService smsService;
    @Value("${sms.enable}")
    Boolean SmsEnable;
    /**
     *
     * @param mobile
     * @return
     */
    @Override
    public RestResponse getSMSCode(String mobile) {
        return smsService.getSmsCode(mobile);
    }

    /**
     * 验证手机号和验证码
     * @param mobile
     * @param key
     * @param code
     * @return
     */
    @Override
    public Integer ckeckMobile(String mobile, String key, String code) {
        smsService.verifyCode(key,code);
        QueryWrapper<Account> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq("mobile",mobile);
        queryWrapper.lambda().eq(Account::getMobile,mobile);
        int one = count(queryWrapper);
        return one>0?1:0;
    }

    /**
     * 注册账户信息
     * @param accountRegisterDTO
     * @return
     */
    @Override
    @Hmily(confirmMethod = "confirmRegister", cancelMethod = "cancelRegister")
    public AccountDTO register(AccountRegisterDTO accountRegisterDTO) {
        Account account = new Account();
        account.setMobile(accountRegisterDTO.getMobile());
        account.setUserName(accountRegisterDTO.getUsername());
        if (SmsEnable){
            account.setPassWord(PasswordUtil.generate(accountRegisterDTO.getPassword()));
        }else {
            account.setPassWord(PasswordUtil.generate(accountRegisterDTO.getMobile()));
        }
        account.setDomain("c");

        try{
            save(account);
        }catch (Exception e){
            e.printStackTrace();
        }

        AccountDTO accountDTO = new AccountDTO();
        if (account != null){
            BeanUtils.copyProperties(account,accountDTO);
            return accountDTO;
        }else {
            return null;
        }

    }

    /**
     * 账户登陆
     * @param accountLoginDTO
     * @return
     */
    @Override
    public AccountDTO login(AccountLoginDTO accountLoginDTO) {
        Account account = null;
        //1.判断用户登陆类型
        //2.c端用户用户名根据手机号查找，b端用户用户名根据账户名查找
        if (accountLoginDTO.getDomain().equalsIgnoreCase("c")){
            account = getAccountByMobile(accountLoginDTO.getMobile());
        }else {
            account = getAccountByUserName(accountLoginDTO.getUsername());
        }
        if (account == null){
            throw new BusinessException(AccountErrorCode.E_130104);
        }
        AccountDTO accountDTO = new AccountDTO();
        BeanUtils.copyProperties(account,accountDTO);
        //3.判断是不是验证码登陆，如果是的话不需要验证密码
        if (SmsEnable){
            return accountDTO;
        }
        //4.进行密码验证
        boolean verify = PasswordUtil.verify(accountLoginDTO.getPassword(), account.getPassWord());
        if (verify){
            return accountDTO;
        }
        throw new BusinessException(AccountErrorCode.E_130105);
    }

    private Account getAccountByMobile(String Mobile){
        return getOne(new QueryWrapper<Account>().lambda().eq(Account::getMobile,Mobile)) ;
    }

    private Account getAccountByUserName(String UserName){
        return getOne(new QueryWrapper<Account>().lambda().eq(Account::getUserName,UserName)) ;
    }

    public void confirmRegister(AccountRegisterDTO registerDTO) {
        log.info("execute confirmRegister");
    }

    public void cancelRegister(AccountRegisterDTO registerDTO) {
        log.info("execute cancelRegister");
        //删除账号
        remove(Wrappers.<Account>lambdaQuery().eq(Account::getUserName,
                registerDTO.getUsername()));
    }
}
