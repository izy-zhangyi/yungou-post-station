package com.yps.http.controller;
import com.google.common.base.Strings;
import com.yps.feignService.UserService;
import com.yps.viewmodel.UserViewModel;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * controller父类
 */
public class BaseController {

    @Autowired
    private HttpServletRequest request; //自动注入request

    @Resource
    private UserService userService;
    /**
     * 返回用户ID
     * @return
     */
    public Integer getUserId(){
        String userId = request.getHeader("userId");
        if(Strings.isNullOrEmpty(userId)){
            return null;
        }else {
//            UserViewModel user = userService.getUser(Integer.parseInt(userId));
//            Map<Object,Object> map = new ConcurrentHashMap<>();
//            map.put("userId", userId);
//            map.put("userName", user.getUserName());
//            return map;
            return Integer.parseInt(userId);
        }
    }

    /**
     * 返回用户名称
     * @return
     */
    public String getUserName(){
        String userName = request.getHeader("userName");
        if(Strings.isNullOrEmpty(userName)){
            if(!Strings.isNullOrEmpty(request.getHeader("userId"))){
                UserViewModel user = userService.getUser(Integer.parseInt(request.getHeader("userId")));
                return user.getUserName();
            }
            return null;
        }
        return userName;
    }

    /**
     * 返回登录类型
     * @return
     */
    public Integer getLoginType(){
        String loginType = request.getHeader("loginType");
        if(Strings.isNullOrEmpty(loginType)){
            return null;
        }else {
            return Integer.parseInt(loginType);
        }
    }
}
