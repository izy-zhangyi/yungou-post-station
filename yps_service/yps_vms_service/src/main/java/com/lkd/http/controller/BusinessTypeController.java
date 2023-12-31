package com.yps.http.controller;

import com.yps.entity.BusinessTypeEntity;
import com.yps.service.BusinessTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("/businessType")
public class BusinessTypeController {
    @Autowired
    private BusinessTypeService businessTypeService;

    /**
     * 获取所有商圈
     * @return List<BusinessTypeEntity>
     */
    @GetMapping()
    public List<BusinessTypeEntity> getAll(){
        return businessTypeService.list();
    }
}
