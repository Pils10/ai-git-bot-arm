package org.remus.giteabot.agent.loop;

/**
 * Strategy contract for one agent flavour driven by {@link AgentLoop}.
 *
 * <p>Concrete strategies (writer, coding, …) own:</p>
 * <ul>
 *     <li>System-prompt selection.</li>
 *     <li>AI-response parsing into a domain plan.</li>
 *     <li>All side effects: tool execution, branch switching, comment posting,
 *         workspace inspection, sub-budget enforcement.</li>
 *     <li>The decision to continue with a follow-up prompt or terminate.</li>
 * </ul>
 *
 * <p>The loop only owns the chat/history/session synchronisation mechanics
 * and the hard {@link AgentBudget#maxRounds()} cap.</p>
 */
public interface AgentStrategy {

    /** System prompt to send on every {@code aiClient.chat} call. */
    String systemPrompt();

    /**
     * Decide what to do given the AI's latest assistant turn.
     *
     * <p>May execute tools, post comments, mutate {@code ctx} (e.g. update
     * {@link AgentRunContext#setBaseBranch}), and update session status.</p>
     *
     * @param ctx           per-run context (also writeable for branch switches)
     * @param aiResponse    raw assistant content from the latest AI call
     * @param round         1-based loop iteration counter
     * @return next decision
     */
    StepDecision step(AgentRunContext ctx, String aiResponse, int round);

    /**
     * Hook called when the loop exhausts {@link AgentBudget#maxRounds()}
     * without the strategy returning a {@link StepDecision.Finish}. Strategies
     * use this to record their own "no success" outcome.
     */
    LoopOutcome onBudgetExhausted(AgentRunContext ctx);
}

