package cn.itcast.wanxinp2p.repayment.config;

import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperConfiguration;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperRegistryCenter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZKRegistryCenterConfig {
    //zookeeper 服务地址
    @Value("${p2p.zookeeper.connString}")
    private String ZOOKEEPER_CONNECTION_STRING;

    @Value("${p2p.job.namespace}")
    private String JOB_NAMESPACE;

    @Bean(initMethod = "init")
    public ZookeeperRegistryCenter setUpRegistryCenter(){
        //配置zookeeper
        ZookeeperConfiguration zookeeperConfiguration = new ZookeeperConfiguration(ZOOKEEPER_CONNECTION_STRING,JOB_NAMESPACE);
        //创建注册中心
        return new ZookeeperRegistryCenter(zookeeperConfiguration);
    }
}
