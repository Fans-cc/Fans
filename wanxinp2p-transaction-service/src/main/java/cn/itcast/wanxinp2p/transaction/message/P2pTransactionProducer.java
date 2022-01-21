package cn.itcast.wanxinp2p.transaction.message;

import cn.itcast.wanxinp2p.api.repayment.model.ProjectWithTendersDTO;
import cn.itcast.wanxinp2p.transaction.entity.Project;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
@Component
@Slf4j
public class P2pTransactionProducer {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public void updateProjectStatusAndStartRepayment(Project project, ProjectWithTendersDTO projectWithTendersDTO) {
        // 1.构造消息
        JSONObject object = new JSONObject();
        object.put("project",project);
        object.put("projectWithTendersDTO",projectWithTendersDTO);

        Message<String> message = MessageBuilder.withPayload(object.toString()).build();
        rocketMQTemplate.sendMessageInTransaction("PID_START_REPAYMENT","TP_START_REPAYMENT",message,null);
    }
}
