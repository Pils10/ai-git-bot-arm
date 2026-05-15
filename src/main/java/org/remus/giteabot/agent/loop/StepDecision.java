package org.remus.giteabot.agent.loop;

/**
 * Result of a single {@link AgentStrategy#step} invocation.
 *
 * <p>The decision is intentionally minimal: either continue with another AI
 * call or terminate the loop with a final outcome. Strategies are responsible
 * for executing tools, posting comments, and tracking sub-budgets internally
 * before returning the next step.</p>
 */
public sealed interface StepDecision {

    /** Continue the loop. The given prompt will be sent as the next user turn. */
    record Continue(String nextUserMessage) implements StepDecision {}

    /** Terminate the loop with the given outcome (success, ask-user, fail, …). */
    record Finish(LoopOutcome outcome) implements StepDecision {}
}

