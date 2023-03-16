package com.yps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yps.contract.VendoutResp;
import com.yps.entity.OrderEntity;
import com.yps.http.viewModel.CreateOrderReq;
import com.yps.http.viewModel.OrderResp;
import com.yps.viewmodel.CreateOrder;
import com.yps.viewmodel.OrderViewModel;
import com.yps.viewmodel.Pager;
import org.springframework.data.domain.Page;

import java.util.List;

public interface OrderService extends IService<OrderEntity> {


    /**
     * 微信小程序支付创建订单
     *
     * @param createOrder
     * @return
     */
    OrderEntity createOrder(CreateOrder createOrder);


    /**
     * 处理出货结果
     *
     * @param vendoutResp 出货请求参数
     * @return
     */
    boolean vendoutResult(VendoutResp vendoutResp);


    /**
     * 通过订单编号获取订单实体
     *
     * @param orderNo
     * @return
     */
    OrderEntity getByOrderNo(String orderNo);

    /**
     * 取消订单
     *
     * @param orderNo
     * @return
     */
    Boolean cancel(String orderNo);

    /**
     * 微信支付完成
     *
     * @param orderNo
     * @return
     */
    Boolean payComplete(String orderNo);

    /**
     * 查询订单
     *
     * @param pageIndex
     * @param pageSize
     * @return
     */
    Pager<OrderViewModel> search(Integer pageIndex, Integer pageSize, String orderNo,
                                 String openId, String startDate, String endDate);

    /**
     * 获取商圈下销量最好的前10商品
     * @param businessId 商圈id
     * @return
     */
    List<Long> getTop10Sku(Integer businessId);
}
