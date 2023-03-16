package com.yps.job;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * xxl-job config
 * 自动维修，自动补货工单 都是需要用到 xxl-job
 */
@Configuration
@Slf4j
public class XxlJobConfig {

    // 1. 从配置文件中获取 xxl-job 的配置信息
    @Value("${xxl.job.accessToken}")
    private String accessToken;

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.executor.appname}")
    private String appName;

    @Value("${xxl.job.executor.address}")
    private String address;

    @Value("${xxl.job.executor.ip}")
    private String ip;

    @Value("${xxl.job.executor.port}")
    private int port;

    @Value("${xxl.job.executor.logretentiondays}")
    private int logRetentionDays;

    // 2. 通过第三方注入的形式，将 xxl-job 的执行器 注入到 ioc容器中
    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor(){
        log.info(">>>>>>>>>>> xxl-job config init.");
        // 获取执行器对象
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        // 填充执行器属性信息
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses); // 任务调度中心请求地址
        xxlJobSpringExecutor.setAppname(appName); // 执行器名称
        xxlJobSpringExecutor.setAddress(address); // 执行器地址
        xxlJobSpringExecutor.setIp(ip); // 执行器 IP
        xxlJobSpringExecutor.setPort(port); // 微服务与调度中心的通信 端口号
        xxlJobSpringExecutor.setAccessToken(accessToken); // token
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays); // 执行器中，日志保留天数
        return xxlJobSpringExecutor;
    }
}
