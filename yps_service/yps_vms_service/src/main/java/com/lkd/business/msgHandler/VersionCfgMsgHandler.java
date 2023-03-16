package com.yps.business.msgHandler;

import com.yps.annotations.ProcessType;
import com.yps.business.MsgHandler;
import com.yps.business.VmCfgService;
import com.yps.config.TopicConfig;
import com.yps.contract.ChannelCfg;
import com.yps.contract.SkuCfg;
import com.yps.contract.SkuPriceCfg;
import com.yps.contract.VersionCfg;
import com.yps.emq.MqttProducer;
import com.yps.entity.VmCfgVersionEntity;
import com.yps.service.VmCfgVersionService;
import com.yps.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 售货机版本配置处理
 */
@Component
@ProcessType(value = "versionCfg")
@Slf4j
public class VersionCfgMsgHandler implements MsgHandler{
    @Autowired
    private VmCfgVersionService versionService;
    @Autowired
    private VmCfgService vmCfgService;
    @Autowired
    private MqttProducer mqttProducer;

    @Override
    public void process(String jsonMsg) throws IOException {
        try{
            VersionCfg versionCfg = JsonUtil.getByJson(jsonMsg, VersionCfg.class);
            if(versionCfg.getData() == null) return;
            VmCfgVersionEntity versionInfo = versionService.getVmVersion(versionCfg.getInnerCode());
            if(versionInfo == null) return;

            if(versionCfg.getData().getSkucfgVersion() < versionInfo.getSkuCfgVersion()){
                //下发商品配置和价格配置
                SkuCfg skuCfg = vmCfgService.getSkuCfg(versionCfg.getInnerCode());
                mqttProducer.send(TopicConfig.TO_VM_TOPIC+versionCfg.getInnerCode(),2,skuCfg);
            }

            if(versionCfg.getData().getSkuPriceCfg() < versionInfo.getPriceCfgVersion()){
                SkuPriceCfg skuPriceCfg = vmCfgService.getSkuPriceCfg(versionCfg.getInnerCode());
                mqttProducer.send(TopicConfig.TO_VM_TOPIC+versionCfg.getInnerCode(),2,skuPriceCfg);
            }

            if(versionCfg.getData().getChannelCfg() < versionInfo.getChannelCfgVersion()){
                ChannelCfg channelCfg = vmCfgService.getChannelCfg(versionCfg.getInnerCode());
                mqttProducer.send(TopicConfig.TO_VM_TOPIC+versionCfg.getInnerCode(),2,channelCfg);
            }
        }catch (Exception e){
            log.error("process versionCfg error. vm request msg is: " + jsonMsg,e);
        }

    }
}

