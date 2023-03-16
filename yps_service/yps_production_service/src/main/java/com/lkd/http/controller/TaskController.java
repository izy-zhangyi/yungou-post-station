package com.yps.http.controller;

import com.yps.entity.*;
import com.yps.exception.LogicException;
import com.yps.feignService.UserService;
import com.yps.http.viewModel.*;
import com.yps.service.*;
import com.yps.viewmodel.Pager;
import com.yps.viewmodel.UserWork;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController extends BaseController {

    private final TaskService taskService;

    private final TaskDetailsService taskDetailsService;

    private final TaskTypeService taskTypeService;

    private final JobService jobService;

    private final TaskCollectService taskCollectService;

    private final UserService userService;

    /**
     * 根据taskId查询
     *
     * @param taskId 工单id
     * @return 实体
     */
    @GetMapping("/taskInfo/{taskId}")
    public TaskEntity findById(@PathVariable Long taskId) {
        return taskService.getById(taskId);
    }

    /**
     * 创建工单
     *
     * @param task 工单实体对象
     * @return
     */
    @PostMapping("/create")
    public boolean create(@RequestBody TaskViewModel task) throws LogicException {
//        Map map = getUserId();
        // 设置当前登录用户ID--工单创建人id
        task.setAssignorId(getUserId());
        // 工单执行人名称
        task.setUserName(userService.getUser(task.getUserId()).getUserName());
        return taskService.createTask(task);
    }

    /**
     * 修改
     *
     * @param taskId 工单id
     * @param task   工单实体对象
     * @return 是否成功
     */
    @PutMapping("/{taskId}")
    public boolean update(@PathVariable Long taskId, @RequestBody TaskEntity task) {
        task.setTaskId(taskId);

        return taskService.updateById(task);
    }


    /**
     * 完成工单
     *
     * @param taskId 工单id
     * @return
     */
    @GetMapping("/complete/{taskId}")
    public boolean complete(@PathVariable("taskId") String taskId,
                            @RequestParam(value = "lat", required = false, defaultValue = "0") Double lat,
                            @RequestParam(value = "lon", required = false, defaultValue = "0") Double lon,
                            @RequestParam(value = "addr", required = false, defaultValue = "") String addr) {
        return taskService.completeTask(Long.valueOf(taskId), lat, lon, addr);
    }


    @GetMapping("/allTaskStatus")
    public List<TaskStatusTypeEntity> getAllStatus() {
        return taskService.getAllStatus();
    }

    /**
     * 获取工单类型
     *
     * @return
     */
    @GetMapping("/typeList")
    public List<TaskTypeEntity> getProductionTypeList() {
        return taskTypeService.list();
    }

    /**
     * 获取工单详情
     *
     * @param taskId 工单id
     * @return
     */
    @GetMapping("/details/{taskId}")
    public List<TaskDetailsEntity> getDetail(@PathVariable long taskId) {
        return taskDetailsService.getByTaskId(taskId);
    }

    /**
     * 搜索工单
     *
     * @param pageIndex
     * @param pageSize
     * @param innerCode 设备编号
     * @param userId    工单所属人Id
     * @param taskCode  工单编号
     * @param status    工单状态
     * @param isRepair  是否是维修工单
     * @return
     */
    @GetMapping("/search")
    public Pager<TaskEntity> search(
            @RequestParam(value = "pageIndex", required = false, defaultValue = "1") Long pageIndex,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Long pageSize,
            @RequestParam(value = "innerCode", required = false, defaultValue = "") String innerCode,
            @RequestParam(value = "userId", required = false, defaultValue = "") Integer userId,
            @RequestParam(value = "taskCode", required = false, defaultValue = "") String taskCode,
            @RequestParam(value = "status", required = false, defaultValue = "") Integer status,
            @RequestParam(value = "isRepair", required = false, defaultValue = "") Boolean isRepair,
            @RequestParam(value = "start", required = false, defaultValue = "") String start,
            @RequestParam(value = "end", required = false, defaultValue = "") String end) {
        return taskService.search(pageIndex, pageSize, innerCode, userId, taskCode, status, isRepair, start, end);
    }


    /**
     * 设置自动补货工单配置
     *
     * @param config
     * @return
     */
    @PostMapping("/autoSupplyConfig")
    public boolean setAutoSupplyConfig(@RequestBody AutoSupplyConfigViewModel config) {
        return jobService.setJob(config.getAlertValue());
    }

    /**
     * 获取补货预警值
     *
     * @return
     */
    @GetMapping("/supplyAlertValue")
    public Integer getSupplyAlertValue() {
        JobEntity jobEntity = jobService.getAlertValue();
        if (jobEntity == null) {
            return 0;
        }

        return jobEntity.getAlertValue();
    }


    /**
     * 接受工单
     *
     * @param taskId 工单id
     * @return
     */
    @GetMapping("/accept/{taskId}")
    public Boolean accept(@PathVariable String taskId) {
        // 根据工单id 获取工单信息
        TaskEntity task = taskService.getById(taskId);

        // 校验工单执行人是不是当前的登录用户---assignor_id == user_id
        if (!Objects.deepEquals(task.getAssignorId(), getUserId())) {
            throw new LogicException("操作非法");
        }

        return taskService.accept(taskId);
    }

    /**
     * 拒绝工单
     *
     * @param taskId              工单id
     * @param cancelTaskViewModel 拒绝理由
     * @return
     */
    @PostMapping("/cancel/{taskId}")
    public Boolean cancelTask(@PathVariable("taskId") String taskId, @RequestBody CancelTaskViewModel cancelTaskViewModel) {
        // 1. 根据id获取工单信息
        TaskEntity task = taskService.getById(taskId);

        // 2. 校验工单执行人是不是当前的登录用户
        if (Objects.deepEquals(task.getAssignorId(), getUserId())) {
            throw new LogicException("操作非法");
        }

        return taskService.cancelTask(taskId, cancelTaskViewModel);
    }

    /**
     * 获取当日工单汇总信息
     *
     * @return
     */
    @GetMapping("/taskReportInfo/{start}/{end}")
    public List<TaskReportInfo> getTaskReportInfo(@PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
                                                  @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        return taskService.getReportInfo(start, end);
    }

    /**
     * 获取用户工作量详情
     *
     * @param userId
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/userWork")
    public UserWork getUserWork(@RequestParam Integer userId,
                                @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
                                @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {

        return taskService.getUserWork(userId, start, end);
    }

    /**
     * 获取工单报表
     *
     * @param start
     * @param end
     * @return
     */
    @GetMapping("/collectReport/{start}/{end}")
    public List<TaskCollectEntity> getTaskCollectReport(@PathVariable String start, @PathVariable String end) {
        return taskCollectService.getTaskReport(LocalDate.parse(start, DateTimeFormatter.ISO_LOCAL_DATE), LocalDate.parse(end, DateTimeFormatter.ISO_LOCAL_DATE));
    }

    /**
     * 获取人员排名
     *
     * @param start
     * @param end
     * @param isRepair
     * @return
     */
    @GetMapping("/userWorkTop10/{start}/{end}/{isRepair}/{regionId}")
    public List<UserWork> getUserWorkTop10(@PathVariable String start, @PathVariable String end, @PathVariable Boolean isRepair, @PathVariable String regionId) {
        return taskService.getUserWorkTop10(
                LocalDate.parse(start, DateTimeFormatter.ISO_LOCAL_DATE),
                LocalDate.parse(end, DateTimeFormatter.ISO_LOCAL_DATE),
                isRepair,
                Long.valueOf(regionId));
    }
}