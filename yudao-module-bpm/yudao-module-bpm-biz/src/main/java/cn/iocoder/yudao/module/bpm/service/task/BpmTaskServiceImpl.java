package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.PageUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.*;
import cn.iocoder.yudao.module.bpm.convert.task.BpmTaskConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmTaskExtDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmTaskExtMapper;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceDeleteReasonEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceResultEnum;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

/**
 * ?????????????????? Service ?????????
 *
 * @author ????????????
 * @author jason
 */
@Slf4j
@Service
public class BpmTaskServiceImpl implements BpmTaskService {

    @Resource
    private TaskService taskService;
    @Resource
    private HistoryService historyService;

    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private BpmTaskExtMapper taskExtMapper;
    @Resource
    private BpmMessageService messageService;

    @Override
    public PageResult<BpmTaskTodoPageItemRespVO> getTodoTaskPage(Long userId, BpmTaskTodoPageReqVO pageVO) {
        // ??????????????????
        TaskQuery taskQuery = taskService.createTaskQuery().taskAssignee(String.valueOf(userId)) // ???????????????
            .orderByTaskCreateTime().desc(); // ??????????????????
        if (StrUtil.isNotBlank(pageVO.getName())) {
            taskQuery.taskNameLike("%" + pageVO.getName() + "%");
        }
        if (pageVO.getBeginCreateTime() != null) {
            taskQuery.taskCreatedAfter(DateUtils.of(pageVO.getBeginCreateTime()));
        }
        if (pageVO.getEndCreateTime() != null) {
            taskQuery.taskCreatedBefore(DateUtils.of(pageVO.getEndCreateTime()));
        }
        // ????????????
        List<Task> tasks = taskQuery.listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());
        if (CollUtil.isEmpty(tasks)) {
            return PageResult.empty(taskQuery.count());
        }

        // ?????? ProcessInstance Map
        Map<String, ProcessInstance> processInstanceMap =
            processInstanceService.getProcessInstanceMap(convertSet(tasks, Task::getProcessInstanceId));
        // ?????? User Map
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
            convertSet(processInstanceMap.values(), instance -> Long.valueOf(instance.getStartUserId())));
        // ????????????
        return new PageResult<>(BpmTaskConvert.INSTANCE.convertList1(tasks, processInstanceMap, userMap),
            taskQuery.count());
    }

    @Override
    public PageResult<BpmTaskDonePageItemRespVO> getDoneTaskPage(Long userId, BpmTaskDonePageReqVO pageVO) {
        // ??????????????????
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery().finished() // ?????????
            .taskAssignee(String.valueOf(userId)) // ???????????????
            .orderByHistoricTaskInstanceEndTime().desc(); // ??????????????????
        if (StrUtil.isNotBlank(pageVO.getName())) {
            taskQuery.taskNameLike("%" + pageVO.getName() + "%");
        }
        if (pageVO.getBeginCreateTime() != null) {
            taskQuery.taskCreatedAfter(DateUtils.of(pageVO.getBeginCreateTime()));
        }
        if (pageVO.getEndCreateTime() != null) {
            taskQuery.taskCreatedBefore(DateUtils.of(pageVO.getEndCreateTime()));
        }
        // ????????????
        List<HistoricTaskInstance> tasks = taskQuery.listPage(PageUtils.getStart(pageVO), pageVO.getPageSize());
        if (CollUtil.isEmpty(tasks)) {
            return PageResult.empty(taskQuery.count());
        }

        // ?????? TaskExtDO Map
        List<BpmTaskExtDO> bpmTaskExtDOs =
            taskExtMapper.selectListByTaskIds(convertSet(tasks, HistoricTaskInstance::getId));
        Map<String, BpmTaskExtDO> bpmTaskExtDOMap = convertMap(bpmTaskExtDOs, BpmTaskExtDO::getTaskId);
        // ?????? ProcessInstance Map
        Map<String, HistoricProcessInstance> historicProcessInstanceMap =
            processInstanceService.getHistoricProcessInstanceMap(
                convertSet(tasks, HistoricTaskInstance::getProcessInstanceId));
        // ?????? User Map
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
            convertSet(historicProcessInstanceMap.values(), instance -> Long.valueOf(instance.getStartUserId())));
        // ????????????
        return new PageResult<>(
            BpmTaskConvert.INSTANCE.convertList2(tasks, bpmTaskExtDOMap, historicProcessInstanceMap, userMap),
            taskQuery.count());
    }

    @Override
    public List<Task> getTasksByProcessInstanceIds(List<String> processInstanceIds) {
        if (CollUtil.isEmpty(processInstanceIds)) {
            return Collections.emptyList();
        }
        return taskService.createTaskQuery().processInstanceIdIn(processInstanceIds).list();
    }

    @Override
    public List<BpmTaskRespVO> getTaskListByProcessInstanceId(String processInstanceId) {
        // ??????????????????
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime().desc() // ??????????????????
                .list();
        if (CollUtil.isEmpty(tasks)) {
            return Collections.emptyList();
        }

        // ?????? TaskExtDO Map
        List<BpmTaskExtDO> bpmTaskExtDOs = taskExtMapper.selectListByTaskIds(convertSet(tasks, HistoricTaskInstance::getId));
        Map<String, BpmTaskExtDO> bpmTaskExtDOMap = convertMap(bpmTaskExtDOs, BpmTaskExtDO::getTaskId);
        // ?????? ProcessInstance Map
        HistoricProcessInstance processInstance = processInstanceService.getHistoricProcessInstance(processInstanceId);
        // ?????? User Map
        Set<Long> userIds = convertSet(tasks, task -> NumberUtils.parseLong(task.getAssignee()));
        userIds.add(NumberUtils.parseLong(processInstance.getStartUserId()));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        // ?????? Dept Map
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(convertSet(userMap.values(), AdminUserRespDTO::getDeptId));

        // ????????????
        return BpmTaskConvert.INSTANCE.convertList3(tasks, bpmTaskExtDOMap, processInstance, userMap, deptMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTask(Long userId, @Valid BpmTaskApproveReqVO reqVO) {
        // ??????????????????
        Task task = checkTask(userId, reqVO.getId());
        // ????????????????????????
        ProcessInstance instance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // ???????????????????????????
        taskService.complete(task.getId(), instance.getProcessVariables());

        // ??????????????????????????????
        taskExtMapper.updateByTaskId(
            new BpmTaskExtDO().setTaskId(task.getId()).setResult(BpmProcessInstanceResultEnum.APPROVE.getResult())
                .setReason(reqVO.getReason()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(Long userId, @Valid BpmTaskRejectReqVO reqVO) {
        Task task = checkTask(userId, reqVO.getId());
        // ????????????????????????
        ProcessInstance instance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // ??????????????????????????????
        processInstanceService.updateProcessInstanceExtReject(instance.getProcessInstanceId(), reqVO.getReason());

        // ?????????????????????????????????
        taskExtMapper.updateByTaskId(
            new BpmTaskExtDO().setTaskId(task.getId()).setResult(BpmProcessInstanceResultEnum.REJECT.getResult())
                    .setEndTime(LocalDateTime.now()).setReason(reqVO.getReason()));
    }

    @Override
    public void updateTaskAssignee(Long userId, BpmTaskUpdateAssigneeReqVO reqVO) {
        // ??????????????????
        Task task = checkTask(userId, reqVO.getId());
        // ???????????????
        updateTaskAssignee(task.getId(), reqVO.getAssigneeUserId());
    }

    @Override
    public void updateTaskAssignee(String id, Long userId) {
        taskService.setAssignee(id, String.valueOf(userId));
    }

    /**
     * ??????????????????????????? ???????????????????????????????????????
     *
     * @param userId ?????? id
     * @param taskId task id
     */
    private Task checkTask(Long userId, String taskId) {
        Task task = getTask(taskId);
        if (task == null) {
            throw exception(TASK_COMPLETE_FAIL_NOT_EXISTS);
        }
        if (!Objects.equals(userId, NumberUtils.parseLong(task.getAssignee()))) {
            throw exception(TASK_COMPLETE_FAIL_ASSIGN_NOT_SELF);
        }
        return task;
    }

    @Override
    public void createTaskExt(Task task) {
        BpmTaskExtDO taskExtDO =
            BpmTaskConvert.INSTANCE.convert2TaskExt(task).setResult(BpmProcessInstanceResultEnum.PROCESS.getResult());
        taskExtMapper.insert(taskExtDO);
    }

    @Override
    public void updateTaskExtComplete(Task task) {
        BpmTaskExtDO taskExtDO = BpmTaskConvert.INSTANCE.convert2TaskExt(task)
                .setResult(BpmProcessInstanceResultEnum.APPROVE.getResult()) // ????????????????????????????????? Complete ????????????????????????????????????
                .setEndTime(LocalDateTime.now());
        taskExtMapper.updateByTaskId(taskExtDO);
    }

    @Override
    public void updateTaskExtCancel(String taskId) {
        // ??????????????????????????????????????????????????????????????????????????????
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                // ??????????????????????????????????????????????????????
                HistoricTaskInstance task = getHistoricTask(taskId);
                if (task == null) {
                    return;
                }

                // ?????????????????????????????????????????????????????????
                BpmTaskExtDO taskExt = taskExtMapper.selectByTaskId(taskId);
                if (taskExt == null) {
                    log.error("[updateTaskExtCancel][taskId({}) ????????????????????????????????????????????????]", taskId);
                    return;
                }
                // ??????????????????????????????????????????
                if (BpmProcessInstanceResultEnum.isEndResult(taskExt.getResult())) {
                    log.error("[updateTaskExtCancel][taskId({}) ????????????({})?????????????????????]", taskId, taskExt.getResult());
                    return;
                }

                // ????????????
                taskExtMapper.updateById(new BpmTaskExtDO().setId(taskExt.getId()).setResult(BpmProcessInstanceResultEnum.CANCEL.getResult())
                        .setEndTime(LocalDateTime.now()).setReason(BpmProcessInstanceDeleteReasonEnum.translateReason(task.getDeleteReason())));
            }

        });
    }

    @Override
    public void updateTaskExtAssign(Task task) {
        BpmTaskExtDO taskExtDO =
            new BpmTaskExtDO().setAssigneeUserId(NumberUtils.parseLong(task.getAssignee())).setTaskId(task.getId());
        taskExtMapper.updateByTaskId(taskExtDO);
        // ????????????????????????????????????????????????????????????????????????????????????????????? ProcessInstance?????????????????????????????????????????????????????????
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ProcessInstance processInstance =
                    processInstanceService.getProcessInstance(task.getProcessInstanceId());
                AdminUserRespDTO startUser = adminUserApi.getUser(Long.valueOf(processInstance.getStartUserId())).getCheckedData();
                messageService.sendMessageWhenTaskAssigned(
                    BpmTaskConvert.INSTANCE.convert(processInstance, startUser, task));
            }
        });
    }

    private Task getTask(String id) {
        return taskService.createTaskQuery().taskId(id).singleResult();
    }

    private HistoricTaskInstance getHistoricTask(String id) {
        return historyService.createHistoricTaskInstanceQuery().taskId(id).singleResult();
    }

}
