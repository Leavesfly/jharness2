package io.leavesfly.jharness2.core.event;

/**
 * 配额超限事件。
 */
public class QuotaExceededEvent extends EngineEvent {

    private final String quotaType;
    private final String detail;

    public QuotaExceededEvent(String userId, String sessionId,
                              String quotaType, String detail) {
        super(userId, sessionId);
        this.quotaType = quotaType;
        this.detail = detail;
    }

    public String getQuotaType() { return quotaType; }
    public String getDetail() { return detail; }

    @Override
    public String getEventType() { return "quota.exceeded"; }
}
