package com.yps.exception;

/**
 * 逻辑异常
 */
public class LogicException extends RuntimeException{
    public LogicException(String errorMsg){
        super(errorMsg);
    }
}
