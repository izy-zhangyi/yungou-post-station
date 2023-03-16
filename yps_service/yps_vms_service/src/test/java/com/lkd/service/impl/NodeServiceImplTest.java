package com.yps.service.impl;

import com.yps.service.NodeService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class NodeServiceImplTest{

    @Autowired
    private NodeService nodeService;
    @Test
    public void findByArea() {
//        Pager<NodeEntity> result = nodeService.findByArea(3,1,10);

        Assert.assertTrue(true);
    }
}
