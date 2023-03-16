package com.yps.business.msgHandler;

import com.google.common.collect.Maps;
import com.yps.annotations.ProcessType;
import com.yps.business.MsgHandler;
import com.yps.business.MsgHandlerContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MsgHandlerContextImp implements ApplicationContextAware, MsgHandlerContext{
    private ApplicationContext ctx;
    private Map<String, MsgHandler> handlerMap = Maps.newHashMap();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
        Map<String,MsgHandler> map = ctx.getBeansOfType(MsgHandler.class);
        map.values().stream().forEach(v->{
            String msgType = v.getClass().getAnnotation(ProcessType.class).value();
            handlerMap.put(msgType,v);
        });
    }

    public MsgHandler getMsgHandler(String msgType){
        return handlerMap.get(msgType);
    }
}
