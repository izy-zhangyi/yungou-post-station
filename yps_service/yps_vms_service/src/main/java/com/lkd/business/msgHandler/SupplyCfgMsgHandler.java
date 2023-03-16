package com.yps.business.msgHandler;

import com.yps.annotations.ProcessType;
import com.yps.business.MsgHandler;
import com.yps.business.VmCfgService;
import com.yps.config.TopicConfig;
import com.yps.contract.ChannelCfg;
import com.yps.contract.SkuCfg;
import com.yps.contract.SkuPriceCfg;
import com.yps.contract.SupplyCfg;
import com.yps.emq.MqttProducer;
import com.yps.emq.MqttService;
import com.yps.entity.VmCfgVersionEntity;
import com.yps.service.VendingMachineService;
import com.yps.service.VmCfgVersionService;
import com.yps.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 补货消息处理
 */
@Component
@ProcessType(value = "supplyResp")
@Slf4j
public class SupplyCfgMsgHandler implements MsgHandler {
    @Autowired
    private VendingMachineService vmService;
    @Autowired
    private VmCfgVersionService versionService;

    @Autowired
    private VmCfgService vmCfgService;

    @Resource
    private MqttProducer mqttProducer; // 注入 消息发送者

    @Override
    public void process(String jsonMsg) throws IOException {
        //解析补货协议
        SupplyCfg supplyCfg = JsonUtil.getByJson(jsonMsg, SupplyCfg.class);
        //更新售货机库存
        vmService.supply(supplyCfg);

        String innerCode = supplyCfg.getInnerCode();//获取售货机编号
        VmCfgVersionEntity vsersion = versionService.getVmVersion(innerCode);//获取版本

        // todo: 主题-- 需要订阅的交换机的主题名称
        String topic = TopicConfig.TO_VM_TOPIC + innerCode;

        //下发商品配置
        SkuCfg skuCfg = vmCfgService.getSkuCfg(innerCode);
        skuCfg.setSn(System.nanoTime()); //纳秒
        skuCfg.setVersionId(vsersion.getSkuCfgVersion());
        //todo: 将商品配置发送到售货机 -- 也就是 发送到 emq 中-- 售货机会订阅消息-从而得到想要的数据
        mqttProducer.send(topic, 2, skuCfg);

        //下发价格配置
        SkuPriceCfg skuPriceCfg = vmCfgService.getSkuPriceCfg(innerCode);
        skuPriceCfg.setSn(System.nanoTime());
        skuPriceCfg.setVersionId(vsersion.getPriceCfgVersion());
        //todo: 将价格配置发送到售货机
        mqttProducer.send(topic, 2, skuPriceCfg);

        //下发货道配置
        ChannelCfg channelCfg = vmCfgService.getChannelCfg(innerCode);
        channelCfg.setSn(System.nanoTime());
        channelCfg.setVersionId(vsersion.getChannelCfgVersion());
        //todo: 将货道配置发送到售货机
        mqttProducer.send(topic, 2, channelCfg);
        //下发补货信息
        supplyCfg.setVersionId(vsersion.getSupplyVersion());
        supplyCfg.setNeedResp(true);
        supplyCfg.setSn(System.nanoTime());
        supplyCfg.setVersionId(vsersion.getSupplyVersion());
        //todo: 将补货信息发送到售货机
        mqttProducer.send(topic, 2, supplyCfg);
    }
}
