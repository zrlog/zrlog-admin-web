package com.zrlog.admin.business.automation;

import com.zrlog.common.Validator;
import com.zrlog.data.security.AccountAccess;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Optional language-model adapter. It proposes plans and has no task mutation or execution API. */
public interface AutomationAssistant {
    boolean available(AccountAccess actor);

    Proposal propose(AccountAccess actor, Request request, List<AutomationModels.Definition> definitions, long now)
            throws Exception;

    class Request implements Validator {
        public String input;
        public String timezone;

        @Override
        public void doValid() {
            if (input == null || input.isBlank() || input.length() > 4000 || timezone == null) {
                throw new IllegalArgumentException("automation.invalidProposalRequest");
            }
            try { ZoneId.of(timezone); }
            catch (java.time.DateTimeException error) { throw new IllegalArgumentException("automation.invalidProposalRequest"); }
        }
    }

    class Proposal {
        public String explanation;
        public List<AutomationModels.Plan> plans = new ArrayList<>();
    }
}
