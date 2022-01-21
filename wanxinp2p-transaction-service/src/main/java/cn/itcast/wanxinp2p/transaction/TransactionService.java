package cn.itcast.wanxinp2p.transaction;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"cn.itcast.wanxinp2p.transaction.agent"})
@MapperScan("cn.itcast.wanxinp2p.transaction.mapper")
public class TransactionService {


    public static void main(String[] args) {
        SpringApplication.run(TransactionService.class, args);
    }


}

