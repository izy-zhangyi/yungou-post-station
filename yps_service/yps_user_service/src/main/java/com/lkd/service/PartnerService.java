package com.yps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yps.entity.PartnerEntity;
import com.yps.http.viewModel.LoginReq;
import com.yps.http.viewModel.LoginResp;
import com.yps.http.viewModel.PartnerReq;
import com.yps.http.viewModel.PartnerUpdatePwdReq;
import com.yps.viewmodel.Pager;

import java.io.IOException;

/**
 * 合作商接口
 */
public interface PartnerService extends IService<PartnerEntity> {
    /**
     * 登录
     * @param req
     * @return
     * @throws IOException
     */
    LoginResp login(LoginReq req) throws IOException;

    /**
     * 更新合作商
     * @param
     * @return
     */
    Boolean modify(Integer id,PartnerReq req);

    /**
     * 删除
     * @param id
     * @return
     */
    boolean delete(Integer id);

    /**
     * 重置密码
     * @param id
     */
    void resetPwd(Integer id);

    /**
     * 查询合作商
     * @param pageIndex
     * @param pageSize
     * @param name
     * @return
     */
    Pager<PartnerEntity> search(Long pageIndex,Long pageSize,String name);

    /**
     * 更新密码
     * @param req
     * @return
     */
    Boolean updatePwd(Integer id,PartnerUpdatePwdReq req);
}
