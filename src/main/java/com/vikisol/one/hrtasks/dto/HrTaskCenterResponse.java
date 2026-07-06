package com.vikisol.one.hrtasks.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrTaskCenterResponse {
    private Map<String, Integer> counts;
    private List<HrTaskItem> bgvPending;
    private List<HrTaskItem> documentsPending;
    private List<HrTaskItem> joiningTomorrow;
    private List<HrTaskItem> probationEnding;
    private List<HrTaskItem> confirmationDue;
    private List<HrTaskItem> resignationPending;
    private List<HrTaskItem> assetCollectionPending;
    // Exit Interview Pending has no backing data source yet (no exit-interview concept exists in
    // the Offboarding module as of this session) - always returned empty with a flag rather than
    // fabricated data, so the frontend can render an honest "not available yet" state.
    private List<HrTaskItem> exitInterviewPending;
    private boolean exitInterviewDataAvailable;
}
