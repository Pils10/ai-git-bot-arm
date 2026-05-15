package org.remus.giteabot.agent.writerimpl;

import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.agent.loop.AgentRunContext;
import org.remus.giteabot.agent.loop.AgentStrategy;
import org.remus.giteabot.agent.loop.LoopOutcome;
import org.remus.giteabot.agent.loop.StepDecision;
import org.remus.giteabot.agent.model.ImplementationPlan;
import org.remus.giteabot.agent.session.AgentSession;
import org.remus.giteabot.agent.session.AgentSessionService;
import org.remus.giteabot.agent.shared.BranchSwitcher;
import org.remus.giteabot.agent.tools.AgentToolRouter;
import org.remus.giteabot.agent.tools.ToolCallContext;
import org.remus.giteabot.agent.validation.ToolResult;
import org.remus.giteabot.repository.RepositoryApiClient;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AgentStrategy} for the technical-writer agent.
 *
 * <p>The strategy is created fresh per loop run and tracks its own context-round
 * sub-budget. The {@code maxToolRounds} parameter mirrors the previous
 * {@code WriterConfig.maxToolRounds} cap.</p>
 */
@Slf4j
public final class WriterAgentStrategy implements AgentStrategy {

    private final String systemPrompt;
    private final WriterPromptBuilder promptBuilder;
    private final WriterResponseParser responseParser;
    private final AgentSessionService sessionService;
    private final RepositoryApiClient repositoryClient;
    private final BranchSwitcher branchSwitcher;
    private final AgentToolRouter toolRouter;
    private final int maxToolRounds;

    public WriterAgentStrategy(String systemPrompt,
                               WriterPromptBuilder promptBuilder,
                               WriterResponseParser responseParser,
                               AgentSessionService sessionService,
                               RepositoryApiClient repositoryClient,
                               BranchSwitcher branchSwitcher,
                               AgentToolRouter toolRouter,
                               int maxToolRounds) {
        this.systemPrompt = systemPrompt;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.sessionService = sessionService;
        this.repositoryClient = repositoryClient;
        this.branchSwitcher = branchSwitcher;
        this.toolRouter = toolRouter;
        this.maxToolRounds = maxToolRounds;
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public StepDecision step(AgentRunContext ctx, String aiResponse, int round) {
        // Writer round counter is 0-based historically: round 1 of the loop == round 0 of the writer.
        int writerRound = round - 1;
        WriterPlan plan = responseParser.parse(aiResponse);

        if (plan.hasContextRequests() && writerRound >= maxToolRounds) {
            sessionService.setStatus(ctx.session(), AgentSession.AgentSessionStatus.IN_PROGRESS);
            repositoryClient.postIssueComment(ctx.owner(), ctx.repo(), ctx.issueNumber(),
                    "⚠️ **AI Technical Writer**: I need more context before I can continue. "
                            + "Please add more details and mention me again.");
            return new StepDecision.Finish(LoopOutcome.success(ctx.baseBranch(), plan));
        }

        if (plan.hasContextRequests() && writerRound < maxToolRounds) {
            List<ImplementationPlan.ToolRequest> contextRequests = buildContextRequests(plan);
            BranchSwitcher.Result branchSwitch = branchSwitcher.apply(
                    ctx.workspaceDir(), ctx.session().getBranchName(), contextRequests, ctx.issueNumber());
            if (branchSwitch.selectedBranch() != null
                    && !branchSwitch.selectedBranch().equals(ctx.session().getBranchName())) {
                sessionService.setBranchName(ctx.session(), branchSwitch.selectedBranch());
                ctx.setBaseBranch(branchSwitch.selectedBranch());
            }
            List<ToolResult> results = executeTools(ctx, branchSwitch.remainingToolRequests());
            return new StepDecision.Continue(
                    promptBuilder.buildToolFeedback(branchSwitch.remainingToolRequests(), results));
        }

        if (plan.hasQuestions() || !plan.isReadyToCreate()) {
            sessionService.setStatus(ctx.session(), AgentSession.AgentSessionStatus.IN_PROGRESS);
            repositoryClient.postIssueComment(ctx.owner(), ctx.repo(), ctx.issueNumber(),
                    promptBuilder.buildClarifyingQuestionComment(plan));
            return new StepDecision.Finish(LoopOutcome.success(ctx.baseBranch(), plan));
        }

        Long createdIssueNumber = repositoryClient.createIssue(ctx.owner(), ctx.repo(),
                "AI Created Issue: " + ctx.session().getIssueTitle(),
                promptBuilder.buildIssueBody(ctx.issueNumber(), plan));
        if (createdIssueNumber == null) {
            sessionService.setStatus(ctx.session(), AgentSession.AgentSessionStatus.FAILED);
            repositoryClient.postIssueComment(ctx.owner(), ctx.repo(), ctx.issueNumber(),
                    "⚠️ **AI Technical Writer**: I drafted the improved issue, but creating it failed. "
                            + "Please check the repository provider response and try again.");
            return new StepDecision.Finish(LoopOutcome.fail(ctx.baseBranch()));
        }
        sessionService.setGeneratedIssueNumber(ctx.session(), createdIssueNumber);
        repositoryClient.postIssueComment(ctx.owner(), ctx.repo(), ctx.issueNumber(),
                "🤖 **AI Technical Writer**: Created improved issue #" + createdIssueNumber
                        + " from this discussion.");
        return new StepDecision.Finish(LoopOutcome.success(ctx.baseBranch(), plan));
    }

    @Override
    public LoopOutcome onBudgetExhausted(AgentRunContext ctx) {
        // Historical writer behaviour: the for-loop simply ends after maxToolRounds+1 iterations
        // without further action when no terminal branch has fired. Mirror that as a no-op success.
        return LoopOutcome.success(ctx.baseBranch(), null);
    }

    private List<ImplementationPlan.ToolRequest> buildContextRequests(WriterPlan plan) {
        List<ImplementationPlan.ToolRequest> requests = new ArrayList<>();
        if (plan.getRequestTools() != null) {
            requests.addAll(plan.getRequestTools());
        }
        if (plan.getRequestFiles() != null) {
            int idx = 1;
            for (String file : plan.getRequestFiles()) {
                requests.add(ImplementationPlan.ToolRequest.builder()
                        .id("writer-file-" + idx)
                        .tool("cat")
                        .args(List.of(file))
                        .build());
                idx++;
            }
        }
        return requests;
    }

    private List<ToolResult> executeTools(AgentRunContext ctx,
                                          List<ImplementationPlan.ToolRequest> requests) {
        List<ToolResult> results = new ArrayList<>();
        for (ImplementationPlan.ToolRequest request : requests) {
            results.add(toolRouter.execute(AgentToolRouter.Mode.WRITER,
                    new ToolCallContext(ctx.owner(), ctx.repo(), ctx.issueNumber(),
                            ctx.workspaceDir(), request)));
        }
        return results;
    }
}

