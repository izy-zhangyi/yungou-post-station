package com.yps.business;

/**
 * 消息处理上下文
 */
public interface MsgHandlerContext{
    MsgHandler getMsgHandler(String msgType);
}
