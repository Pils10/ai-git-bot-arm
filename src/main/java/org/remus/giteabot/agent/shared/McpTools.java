package org.remus.giteabot.agent.shared;

import org.remus.giteabot.mcp.McpOrchestrationService;
import org.remus.giteabot.mcp.McpToolCatalog;

/**
 * Null-safe MCP-tool detection helper. Both agent services need to ask whether a
 * tool name belongs to the configured MCP catalog; before this helper existed the
 * check was duplicated in {@code IssueImplementationService} and
 * {@code WriterAgentService}.
 */
public final class McpTools {

    private McpTools() {
    }

    public static boolean isMcpTool(McpOrchestrationService orchestration,
                                    McpToolCatalog catalog,
                                    String toolName) {
        return orchestration != null && orchestration.isMcpTool(catalog, toolName);
    }
}

