package com.yps.feignService.fallback;

import com.google.common.collect.Lists;
import com.yps.feignService.VMService;
import com.yps.viewmodel.*;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class VmServiceFallbackFactory implements FallbackFactory<VMService> {
    @Override
    public VMService create(Throwable throwable) {
        log.error("调用售货机服务失败",throwable);
        return new VMService() {
            @Override
            public Integer getNodeCountByOwnerId(Integer ownerId) {
                return null;
            }

            @Override
            public Integer getVmCountByOwnerId(Integer ownerId) {
                return null;
            }

            @Override
            public VendingMachineViewModel getVMInfo(String innerCode) {
                return null;
            }
            @Override
            public void inventory(int percent) {

            }

            @Override
            public List<SkuViewModel> getAllSkuByInnerCode(String innerCode) {
                return Lists.newArrayList();
            }

            @Override
            public SkuViewModel getSku(String innerCode, String skuId) {
                return null;
            }

            @Override
            public SkuViewModel getSkuById(long skuId) {
                return null;
            }

            @Override
            public RegionViewModel getRegionById(String regionId) {
                RegionViewModel viewModel = new RegionViewModel();

                return viewModel;
            }

            @Override
            public String getNodeName(Long id) {
                return null;
            }

            @Override
            public Boolean hasCapacity(String innerCode, String skuId) {
                return false;
            }

            @Override
            public List<VmInfoDto> search(VmSearch vmSearch) {
                return Lists.newArrayList();
            }
        };
    }
}
