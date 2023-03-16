package com.yps.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yps.common.VMSystem;
import com.yps.entity.TaskCollectEntity;
import com.yps.entity.TaskEntity;
import com.yps.service.TaskCollectService;
import com.yps.service.TaskService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日工单数据汇总(汇总昨天数据)
 *
 * @return
 */
@Component
@RequiredArgsConstructor
public class TaskCollectJob {

    private final TaskService taskService;
    private final TaskCollectService taskCollectService;

    @XxlJob("taskCollectJobHandler")
    public ReturnT<String> taskCollectJobHandler(String param) {
        TaskCollectEntity taskCollectEntity = new TaskCollectEntity();// 获取每日工单统计封装对象
        LocalDate start = LocalDate.now().plusDays(-1); // 获取上一天的日期

        // 进行中工单
        taskCollectEntity.setProgressCount(this.count(start, VMSystem.TASK_STATUS_PROGRESS));
        //取消或拒绝的工单
        taskCollectEntity.setCancelCount(this.count(start, VMSystem.TASK_STATUS_CANCEL));
        //完成的工单
        taskCollectEntity.setFinishCount(this.count(start, VMSystem.TASK_STATUS_FINISH));
        taskCollectEntity.setCollectDate(start); // 日期
        // 先清除某天数据
        cleanup(start);
        taskCollectService.save(taskCollectEntity);
        // 无效工单处理
        cleanupTask();
        return ReturnT.SUCCESS;
    }

    private int count(LocalDate start, Integer taskStatus) {
        LambdaQueryWrapper<TaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskEntity::getTaskStatus, taskStatus)
                .ge(TaskEntity::getCreateTime, start).le(TaskEntity::getUpdateTime, start.plusDays(1));
        return taskService.count(queryWrapper);
    }

    /**
     * 清除某天数据
     */
    private void cleanup(LocalDate start) {
        LambdaQueryWrapper<TaskCollectEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskCollectEntity::getCollectDate, start);
        taskCollectService.remove(queryWrapper);
    }

    /**
     * 无效工单处理
     */
    private void cleanupTask() {
        LambdaUpdateWrapper<TaskEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.lt(TaskEntity::getUpdateTime, LocalDate.now())
                .and(w -> w.eq(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_PROGRESS)
                        .or()
                        .eq(TaskEntity::getTaskStatus, VMSystem.TASK_STATUS_CREATE))
                .set(TaskEntity::getTaskStatus,VMSystem.TASK_STATUS_CANCEL)
                .set(TaskEntity::getDesc,"工单超时");
        taskService.update(updateWrapper);
    }
}
