package org.remus.giteabot.agent.tools;

/**
 * Classification of a tool the AI agent can invoke. The canonical lookup is
 * {@link ToolCatalog#kindOf(String)} — code should not duplicate this taxonomy
 * by maintaining its own constant lists or {@code is*Tool} checks.
 */
public enum ToolKind {

    /**
     * Read-only repository exploration (rg, cat, find, tree, git-log, …) plus the
     * special {@code branch-switcher} workspace operation. Results are silent —
     * never posted as public comments.
     */
    CONTEXT,

    /**
     * Workspace-mutating file tools (write-file, patch-file, mkdir, delete-file).
     * Silent.
     */
    FILE,

    /**
     * External validation tools (mvn, npm, gradle, …) configured via
     * {@code AgentConfigProperties.validation.available-tools}. Results ARE
     * posted as comments.
     */
    VALIDATION,

    /**
     * A tool exposed by an MCP server (name prefix {@code mcp:}). Silent.
     */
    MCP,

    /**
     * Repository API helpers used by the writer agent (get-issue, search-issues).
     * Silent.
     */
    REPOSITORY,

    /**
     * Tool name is not configured for the current agent role.
     */
    UNKNOWN
}
