package org.remus.giteabot.agent.writerimpl;

import lombok.Builder;
import lombok.Data;
import org.remus.giteabot.agent.model.ImplementationPlan;

import java.util.List;

@Data
@Builder
public class WriterPlan {
    private String qualityAssessment;
    private List<String> clarifyingQuestions;
    private String revisedIssueDraft;
    private List<String> assumptions;
    private List<String> openQuestions;
    private boolean readyToCreate;
    private List<ImplementationPlan.ToolRequest> requestTools;

    public boolean hasQuestions() {
        return clarifyingQuestions != null && !clarifyingQuestions.isEmpty();
    }

    public boolean hasToolRequests() {
        return requestTools != null && !requestTools.isEmpty();
    }
}
