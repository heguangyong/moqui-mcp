package org.moqui.mcp;

import org.moqui.context.ExecutionContext;
import org.moqui.entity.EntityValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class McpProjectService {
    private static final Logger logger = LoggerFactory.getLogger(McpProjectService.class);
    private final ExecutionContext ec;

    public McpProjectService(ExecutionContext ec) {
        this.ec = ec;
    }

    /**
     * 更新项目阶段数据
     */
    public Map<String, Object> updatePhaseData(Map<String, Object> context) {
        String sessionId = (String) context.get("sessionId");
        String phase = (String) context.get("phase");
        String data = (String) context.get("data");

        try {
            // 更新会话上下文
            EntityValue session = ec.getEntity().find("McpDialogSession")
                    .condition("sessionId", sessionId)
                    .one();

            if (session != null) {
                String existingContext = (String) session.get("context");
                String updatedContext = updateContextWithPhaseData(existingContext, phase, data);

                ec.getService().sync().name("update#McpDialogSession").parameters(Map.of(
                        "sessionId", sessionId,
                        "context", updatedContext,
                        "lastModifiedDate", ec.getUser().getNowTimestamp()
                )).call();

                // 如果完成了任务阶段，创建实际的项目工作项
                if ("task".equals(phase)) {
                    createProjectWorkEffort(sessionId, data);
                }
            }

            return Map.of("success", true);

        } catch (Exception e) {
            logger.error("Error updating phase data", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 创建实际的项目工作项
     */
    private void createProjectWorkEffort(String sessionId, String taskData) {
        try {
            EntityValue session = ec.getEntity().find("McpDialogSession")
                    .condition("sessionId", sessionId)
                    .one();

            String projectId = ec.getEntity().sequencedIdPrimary("org.moqui.mcp.McpProject."+sessionId,null,10L);

            // 创建主项目
            ec.getService().sync().name("create#mantle.work.effort.WorkEffort").parameters(Map.of(
                    "workEffortId", projectId,
                    "workEffortName", "AI生成营销项目",
                    "workEffortTypeEnumId", "WetProject",
                    "statusId", "WeInPlanning",
                    "ownerPartyId", session.get("customerId"),
                    "description", "基于AI对话生成的营销项目方案"
            )).call();

            // 更新会话关联的项目ID
            ec.getService().sync().name("update#McpDialogSession").parameters(Map.of(
                    "sessionId", sessionId,
                    "projectId", projectId
            )).call();

            // 解析任务数据并创建子任务（简化版本）
            createSubTasks(projectId, taskData);

        } catch (Exception e) {
            logger.error("Error creating project work effort", e);
        }
    }

    /**
     * 创建子任务
     */
    private void createSubTasks(String projectId, String taskData) {
        // 这里是简化版本，实际项目中需要更复杂的任务解析逻辑
        String[] tasks = taskData.split("\n");

        for (int i = 0; i < tasks.length; i++) {
            String task = tasks[i].trim();
            if (!task.isEmpty() && !task.startsWith("#")) {
                String taskId = ec.getEntity().sequencedIdPrimary("org.moqui.mcp.McpProject."+projectId,null,10L);

                ec.getService().sync().name("create#mantle.work.effort.WorkEffort").parameters(Map.of(
                        "workEffortId", taskId,
                        "parentWorkEffortId", projectId,
                        "workEffortName", task,
                        "workEffortTypeEnumId", "WetTask",
                        "statusId", "WeInPlanning",
                        "sequenceNum", i + 1
                )).call();
            }
        }
    }

    /**
     * 更新上下文中的阶段数据
     */
    private String updateContextWithPhaseData(String existingContext, String phase, String data) {
        StringBuilder context = new StringBuilder();

        if (existingContext != null && !existingContext.isEmpty()) {
            context.append(existingContext).append("\n\n");
        }

        context.append("=== ").append(getPhaseDescription(phase)).append(" ===\n");
        context.append(data);

        return context.toString();
    }

    private String getPhaseDescription(String phase) {
        return switch (phase) {
            case "requirement" -> "需求确认阶段";
            case "design" -> "功能设计阶段";
            case "task" -> "任务分解阶段";
            default -> "未知阶段";
        };
    }
}