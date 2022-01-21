package cn.itcast.wanxinp2p.account.service;

import cn.itcast.wanxinp2p.account.entity.Account;
import cn.itcast.wanxinp2p.api.account.model.AccountDTO;
import cn.itcast.wanxinp2p.api.account.model.AccountLoginDTO;
import cn.itcast.wanxinp2p.api.account.model.AccountRegisterDTO;
import cn.itcast.wanxinp2p.common.domain.RestResponse;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AccountService extends IService<Account>{
    /**
     * 获取手机验证码
     * @param mobile
     * @return
     */
    RestResponse getSMSCode(String mobile);

    /**
     * 验证手机号和验证码
     * @param mobile
     * @param key
     * @param code
     * @return
     */
    Integer ckeckMobile(String mobile,String key,String code);

    /**
     * 账户注册
     * @param accountRegisterDTO
     * @return
     */
    AccountDTO register(AccountRegisterDTO accountRegisterDTO);

    /**
     * 账户登陆
     * @param accountLoginDTO
     * @return
     */
    AccountDTO login(AccountLoginDTO accountLoginDTO);
}
