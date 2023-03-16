package com.yps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yps.entity.TaskEntity;
import com.yps.entity.TaskStatusTypeEntity;
import com.yps.exception.LogicException;
import com.yps.http.viewModel.CancelTaskViewModel;
import com.yps.http.viewModel.TaskReportInfo;
import com.yps.http.viewModel.TaskViewModel;
import com.yps.viewmodel.Pager;
import com.yps.viewmodel.UserWork;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TaskService extends IService<TaskEntity> {


    /**
     * 创建工单
     *
     * @param taskViewModel
     * @return
     */
    boolean createTask(TaskViewModel taskViewModel) throws LogicException;


    /**
     * 完成工单
     *
     * @param id
     * @return
     */
    boolean completeTask(long id);

    /**
     * 完成工单
     *
     * @param id
     * @param lat 纬度
     * @param lon 经度
     * @return
     */
    boolean completeTask(long id, Double lat, Double lon, String addr);

    /**
     * 获取所有状态类型
     *
     * @return
     */
    List<TaskStatusTypeEntity> getAllStatus();

    /**
     * 通过条件搜索工单列表
     *
     * @param pageIndex
     * @param pageSize
     * @param innerCode
     * @param userId
     * @param taskCode
     * @param isRepair  是否是运维工单
     * @return
     */
    Pager<TaskEntity> search(Long pageIndex, Long pageSize, String innerCode, Integer userId, String taskCode, Integer status, Boolean isRepair, String start, String end);

    /**
     * 接受工单
     *
     * @param id
     * @return
     */
    Boolean accept(String id);

    /**
     * 拒绝工单
     *
     * @param id                  工单id
     * @param cancelTaskViewModel 拒绝理由
     * @return
     */
    Boolean cancelTask(String id, CancelTaskViewModel cancelTaskViewModel);

    /**
     * 获取某一个区域，一天内工单最少的人
     *
     * @param regionId 区域id
     * @param isRepair 是否是维修工单
     * @return
     */
    Integer getTaskLastUser(String regionId, Boolean isRepair);

    /**
     * 获取工单的统计情况
     *
     * @return
     */
    List<TaskReportInfo> getReportInfo(LocalDateTime start, LocalDateTime end);

    /**
     * 获取用户工作量详情
     *
     * @param userId
     * @param start
     * @param end
     * @return
     */
    UserWork getUserWork(Integer userId, LocalDateTime start, LocalDateTime end);

    /**
     * 获取排名前10的工作量
     *
     * @param start
     * @param end
     * @return
     */
    List<UserWork> getUserWorkTop10(LocalDate start, LocalDate end, Boolean isRepair, Long regionId);
}
