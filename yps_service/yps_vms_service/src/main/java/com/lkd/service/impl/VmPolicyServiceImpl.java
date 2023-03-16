package com.yps.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yps.dao.VmPolicyDao;
import com.yps.entity.VmPolicyEntity;
import com.yps.service.VmPolicyService;
import org.springframework.stereotype.Service;

@Service
public class VmPolicyServiceImpl extends ServiceImpl<VmPolicyDao,VmPolicyEntity> implements VmPolicyService{
}
