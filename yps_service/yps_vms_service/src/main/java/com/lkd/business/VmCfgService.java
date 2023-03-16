package com.yps.business;

import com.google.common.collect.Lists;
import com.yps.business.converter.ChannelConverter;
import com.yps.contract.*;
import com.yps.entity.ChannelEntity;
import com.yps.entity.SkuEntity;
import com.yps.entity.VmCfgVersionEntity;
import com.yps.service.ChannelService;
import com.yps.service.SkuService;
import com.yps.service.VendingMachineService;
import com.yps.service.VmCfgVersionService;
import com.yps.viewmodel.SkuViewModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VmCfgService{
    @Autowired
    private VendingMachineService vmService;
    @Autowired
    private VmCfgVersionService versionService;
    @Autowired
    private ChannelService channelService;
    @Autowired
    private SkuService skuService;

    public ChannelCfg getChannelCfg(String innerCode){
        VmCfgVersionEntity version = versionService.getVmVersion(innerCode);
        long versionId = 0L;
        if(version != null){
            versionId = version.getChannelCfgVersion();
        }
        List<ChannelEntity> channelEntityList = vmService.getAllChannel(innerCode);
        ChannelCfg channelCfg = new ChannelCfg();
        List<Channel> channels = Lists.newArrayList();
        channelEntityList.forEach(c -> channels.add(ChannelConverter.convert(c)));
        channelCfg.setChannels(channels);
        channelCfg.setVersionId(versionId);
        channelCfg.setSn(System.nanoTime());
        channelCfg.setNeedResp(true);

        return channelCfg;
    }


    public SkuCfg getSkuCfg(String innerCode){
        VmCfgVersionEntity version = versionService.getVmVersion(innerCode);
        long versionId = 0L;
        if(version != null){
            versionId = version.getSkuCfgVersion();
        }
        SkuCfg skuCfg = new SkuCfg();
        List<SkuViewModel> skuList = vmService.getSkuList(innerCode);
        List<Sku> skus = Lists.newArrayList();
        skuList.forEach(s->{
            SkuEntity skuEntity = skuService.getById(s.getSkuId());
            Sku sku = new Sku();
            sku.setClassId(skuEntity.getClassId());
            sku.setClassName(skuEntity.getSkuClass().getClassName());
            sku.setDiscount(skuEntity.isDiscount());
            sku.setIndex(0);
            sku.setPrice(s.getPrice());
            sku.setSkuId(s.getSkuId());
            sku.setSkuName(s.getSkuName());
            sku.setUnit(s.getUnit());
            skus.add(sku);
        });
        skuCfg.setSkus(skus);
        skuCfg.setVersionId(versionId);
        skuCfg.setInnerCode(innerCode);
        skuCfg.setNeedResp(true);

        return skuCfg;
    }

    public SkuPriceCfg getSkuPriceCfg(String innerCode){
        SkuPriceCfg cfg = new SkuPriceCfg();
        VmCfgVersionEntity version = versionService.getVmVersion(innerCode);
        long versionId = 0L;
        if(version != null){
            versionId = version.getSkuCfgVersion();
        }
        cfg.setInnerCode(innerCode);
        cfg.setNeedResp(true);

        cfg.setVersionId(versionId);
        List<SkuViewModel> skuList = vmService.getSkuList(innerCode);
        List<SkuPrice> skuPrices = Lists.newArrayList();
        skuList.forEach(s->{
            SkuPrice skuPrice = new SkuPrice();
            skuPrice.setDiscount(s.isDiscount());
            skuPrice.setPrice(s.getPrice());
            skuPrice.setRealPrice(channelService.getRealPrice(innerCode,s.getSkuId()));
            cfg.setSkuPrice(skuPrices);
        });

        return cfg;
    }
}
