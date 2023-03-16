package com.yps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yps.entity.VmStatusInfoEntity;
import com.yps.http.viewModel.VmStatusVM;
import com.yps.viewmodel.Pager;

import java.io.IOException;

public interface VmStatusInfoService extends IService<VmStatusInfoEntity> {
    Pager<VmStatusVM> getAll(long pageIndex, long pageSize);
    void setVmStatus(String innerCode,String statusCode,boolean status) throws IOException;
    VmStatusVM getVMStatus(String innerCode);
    boolean isOnline(String innerCode);
    Pager<VmStatusVM> getAllTrouble(long pageIndex, long pageSize);
}
