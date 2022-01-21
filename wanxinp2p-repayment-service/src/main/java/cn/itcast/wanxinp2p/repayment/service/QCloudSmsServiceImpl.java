package cn.itcast.wanxinp2p.repayment.service;


import com.github.qcloudsms.SmsSingleSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
@Slf4j
@Service
public class QCloudSmsServiceImpl implements SmsService{

    @Value("${sms.qcloud.appId}")
    private int appId;
    @Value("${sms.qcloud.appKey}")
    private String appKey;
    @Value("${sms.qcloud.templateId}")
    private int templateId;
    @Value("${sms.qcloud.sign}")
    private String sign;

    @Override
    public void sendRepaymentNotify(String mobile, String date, BigDecimal amount) {
        log.info("给手机号{} , 发送还款提醒: {} , 金额:{} ",mobile,date,amount);
        SmsSingleSender ssender = new SmsSingleSender(appId, appKey);
        try {
            ssender.sendWithParam("86",mobile,templateId,new String[]{date.toString()},sign,"","");
        }catch (Exception e){
            log.error("发送失败：{}",e.getMessage());
        }


    }
}
