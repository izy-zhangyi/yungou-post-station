package com.yps.emq;

import com.google.common.base.Strings;
import com.yps.business.MsgHandler;
import com.yps.business.MsgHandlerContext;
import com.yps.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class MqttServiceImpl implements MqttService{
    @Autowired
    private MsgHandlerContext msgHandlerContext;

    /**
     * mqtt消息处理
     * @param topic
     * @param message
     */
    @Override
    public void processMessage(String topic, MqttMessage message) {
        String msgContent = new String(message.getPayload()); // 解析消息
        log.info("接收到消息:"+msgContent);
        try {
            // 从消息中提取 msgType
            String msgType = JsonUtil.getValueByNodeName("msgType",msgContent);
            if(Strings.isNullOrEmpty(msgType)) return;
            // 通过协议 找到对应的 类型MsgHandler
            MsgHandler msgHandler = msgHandlerContext.getMsgHandler(msgType);
            if(msgHandler == null)return;
            msgHandler.process(msgContent);
        } catch (IOException e) {
            log.error("process msg error,msg is: "+msgContent,e);
        }
    }
}
