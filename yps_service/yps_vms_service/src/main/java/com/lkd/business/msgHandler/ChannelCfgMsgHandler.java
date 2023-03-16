package com.yps.business.msgHandler;

import com.google.common.collect.Lists;
import com.yps.annotations.ProcessType;
import com.yps.business.MsgHandler;
import com.yps.config.TopicConfig;
import com.yps.contract.Channel;
import com.yps.contract.ChannelCfg;
import com.yps.emq.MqttProducer;
import com.yps.entity.ChannelEntity;
import com.yps.entity.VmCfgVersionEntity;
import com.yps.service.VendingMachineService;
import com.yps.service.VmCfgVersionService;
import com.yps.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 处理货道请求
 */
@Component
@ProcessType(value = "channelCfgReq")
public class ChannelCfgMsgHandler implements MsgHandler{
    @Autowired
    private VmCfgVersionService versionService;
    @Autowired
    private VendingMachineService vmService;
    @Autowired
    private MqttProducer mqttProducer;
    @Override
    public void process(String jsonMsg) throws IOException {
        String innerCode = JsonUtil.getNodeByName("vmId",jsonMsg).asText();
        long sn = JsonUtil.getNodeByName("sn",jsonMsg).asLong();
        VmCfgVersionEntity version = versionService.getVmVersion(innerCode);
        long versionId = 0L;
        if(version != null){
            versionId = version.getSkuCfgVersion();
        }
        ChannelCfg cfg = new ChannelCfg();
        cfg.setInnerCode(innerCode);
        cfg.setSn(sn);
        cfg.setVersionId(versionId);
        List<ChannelEntity> channelEntityList = vmService.getAllChannel(innerCode);
        List<Channel> channels = Lists.newArrayList();
        channelEntityList.forEach(c->{
            Channel channelContract =
                    new Channel();
            channelContract.setSkuId(c.getSkuId());
            channelContract.setChannelId(c.getChannelCode());
            channelContract.setCapacity(c.getMaxCapacity());
            channels.add(channelContract);
        });
        cfg.setChannels(channels);
        cfg.setNeedResp(true);
        mqttProducer.send(TopicConfig.TO_VM_TOPIC,2,cfg);
    }
}

