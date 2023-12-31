package com.yps.business;

import com.yps.annotations.ProcessType;
import com.yps.contract.VmStatusContract;
import com.yps.service.VmStatusInfoService;
import com.yps.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ProcessType(value = "vmStatus")
@Slf4j
public class VMStatusHandler implements MsgHandler{
    @Autowired
    private VmStatusInfoService statusInfoService;
    @Override
    public void process(String jsonMsg) throws IOException {
        VmStatusContract vmStatusContract = JsonUtil.getByJson(jsonMsg,VmStatusContract.class);
        if(vmStatusContract == null || vmStatusContract.getStatusInfo() == null || vmStatusContract.getStatusInfo().size() <= 0) return;
        vmStatusContract.getStatusInfo().forEach(s->{
            try {
                statusInfoService.setVmStatus(vmStatusContract.getInnerCode(),s.getStatusCode(),s.isStatus());
            }catch (Exception ex){
                log.error("设置设备状态出错,innerCode:"+vmStatusContract.getInnerCode(),ex);
            }
        });
    }
}
