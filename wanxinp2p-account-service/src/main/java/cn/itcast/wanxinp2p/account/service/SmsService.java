package cn.itcast.wanxinp2p.account.service;


import cn.itcast.wanxinp2p.account.common.AccountErrorCode;
import cn.itcast.wanxinp2p.common.domain.BusinessException;
import cn.itcast.wanxinp2p.common.domain.CommonErrorCode;
import cn.itcast.wanxinp2p.common.domain.RestResponse;
import cn.itcast.wanxinp2p.common.util.OkHttpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    @Value("${sms.url}")
    String SmsUrl;
    @Value("${sms.enable}")
    Boolean SmsEnable;

    /**
     *
     * @param mobile
     * @return
     */
    RestResponse getSmsCode (String mobile){
        if (SmsEnable){
            return OkHttpUtil.post(SmsUrl+"generate?effectiveTime=300&name=sms","{\"mobile\":"+mobile+"}");
        }else {
            return  RestResponse.success();
        }

    }

    void verifyCode(String key , String code) {
        if (SmsEnable) {
            StringBuilder stringBuilder = new StringBuilder("verify?name=sms");
            stringBuilder.append("&verificationKey=").append(key)
                    .append("&verificationCode=").append(code);
            RestResponse post = OkHttpUtil.post(SmsUrl + stringBuilder, "");
            if (post.getCode() != CommonErrorCode.SUCCESS.getCode()
                    || post.getResult().toString().equalsIgnoreCase("false")) {
                throw new BusinessException(AccountErrorCode.E_140152);
            }
        }
    }
}
