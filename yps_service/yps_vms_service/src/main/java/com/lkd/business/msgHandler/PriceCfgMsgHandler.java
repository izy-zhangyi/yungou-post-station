package com.yps.business.msgHandler;

import com.yps.annotations.ProcessType;
import com.yps.business.MsgHandler;
import com.yps.business.VmCfgService;
import com.yps.config.TopicConfig;
import com.yps.contract.SkuPriceCfg;
import com.yps.emq.MqttProducer;
import com.yps.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 商品价格变更
 */
@Component
@ProcessType(value = "skuPrice")
public class PriceCfgMsgHandler implements MsgHandler{
    @Autowired
    private VmCfgService vmCfgService;
    @Autowired
    private MqttProducer mqttProducer;
    @Override
    public void process(String jsonMsg) throws IOException {
        String innerCode = JsonUtil.getValueByNodeName("innerCode",jsonMsg);
        long sn = JsonUtil.getNodeByName("sn",jsonMsg).asLong();
        SkuPriceCfg cfg = vmCfgService.getSkuPriceCfg(innerCode);
        cfg.setSn(sn);
        mqttProducer.send(TopicConfig.TO_VM_TOPIC,2,cfg);
    }
}

