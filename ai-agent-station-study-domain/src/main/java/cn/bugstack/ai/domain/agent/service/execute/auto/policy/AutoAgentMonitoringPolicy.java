package cn.bugstack.ai.domain.agent.service.execute.auto.policy;

/**
 * Runtime guardrails for the Grafana/Prometheus auto agent.
 */
public final class AutoAgentMonitoringPolicy {

    private static final String GRAFANA_AGENT_ID = "5";

    private static final String COMMON_POLICY = """

            # Agent 5 monitoring policy
            Apply these rules when the user did not explicitly provide a different rule:
            1. The default observation window is the latest 24 hours. Determine whether business traffic exists with
               sum(increase(<http_request_count>{<service-filter>,method!="OPTIONS",status!~"404|405",uri!~"/actuator.*|root|/\\\\*\\\\*"}[24h])).
            2. Expand the same instant query to 48h, then 72h, only when the filtered business request total is empty or zero.
               Stop expanding as soon as business traffic exists. After a zero/empty 72h result, complete with an explicit
               no-business-data conclusion instead of requesting another round.
            3. An aggregate query must use queryType="instant", startTime="now", and a PromQL lookback such as [24h].
               Never combine an instant query with startTime="now-24h" to represent the latest 24 hours.
            4. A trend query must use queryType="range", startTime="now-<selected-window>", endTime="now",
               stepSeconds=300, and normally a 5m rate window.
            5. Business performance queries and rankings must exclude method="OPTIONS", status="404"/"405", and URI
               values matching /actuator.*, root, or /**. Preserve the raw data and show 404/405 or excluded routes only
               in a separate non-business/anomaly section when relevant.
            6. HTTP request rate is QPS/RPS, not business TPS. Report TPS only from a real business transaction counter.
               Prefer transaction types raffle_draw, credit_exchange, and calendar_sign with a bounded result label such
               as success/failure. If no such metric exists, state that TPS is unavailable and do not reuse QPS as TPS.
            7. Low traffic is a traffic characteristic, not evidence that the service is unhealthy.
            8. Names wrapped in angle brackets are planning placeholders. Replace them with discovered metric names and
               label filters before calling a tool; never send a placeholder in PromQL.
            """;

    private static final String ANALYZER_POLICY = """

            # Analyzer requirements
            - In the first round, plan datasource/metric discovery and the 24h business-traffic probe in the same round.
            - Reuse a verified datasource UID, metric name, and label set in later rounds. Do not repeat discovery unless
              previous evidence is missing, stale, or contradictory.
            - Base 24h -> 48h -> 72h expansion only on the filtered HTTP request total, not on an empty percentile or error series.
            - Once traffic is found, plan the requested aggregate metrics and the 5-minute-step trend for that same window.
            """;

    private static final String EXECUTOR_POLICY = """

            # Executor requirements
            - Execute the planned 24h business-traffic probe immediately after required discovery. Use increase() for sparse
              aggregate counts and rate(...[5m]) for range trends.
            - When traffic exists, query all user-requested HTTP metrics for the selected window without spending another
              round on an equivalent topk query. A topk query must filter values greater than zero when its goal is finding
              active interfaces.
            - Discover a real business transaction counter before querying TPS. Recommended bounded transaction values are
              raffle_draw, credit_exchange, and calendar_sign. Without that counter, output QPS/RPS and mark TPS unavailable.
            - Include the exact queryType, startTime, endTime, stepSeconds, PromQL, selected window, and applied exclusions
              in the execution result.
            """;

    private static final String SUPERVISOR_POLICY = """

            # Supervisor requirements
            - Verify that latest-window aggregate queries use instant/startTime=now and that range trends use explicit start/end.
            - Do not request 48h or 72h expansion when the previous filtered business request total is greater than zero.
            - Do not request another datasource lookup after a trustworthy UID has been verified.
            - Do not accept HTTP QPS/RPS relabeled as TPS. Missing real transaction instrumentation is a reported limitation,
              not a reason to fabricate TPS or repeatedly optimize.
            - PASS when the user's requested metrics are delivered, or when the 72h business probe is empty and that limitation
              is clearly reported. Do not make unrequested P95/P99 or error rate mandatory completion criteria.
            """;

    private static final String SUMMARY_POLICY = """

            # Final report requirements
            - State the selected 24h/48h/72h window and distinguish aggregate values from 5-minute-step trends.
            - Use separate sections for business HTTP QPS/RPS, real business TPS, and excluded/non-business traffic.
            - Never label an HTTP request rate as TPS. When no transaction counter was queried, say that real TPS is unavailable.
            - Exclude Actuator, root, /**, and OPTIONS from the business table. Do not infer poor health from low traffic alone.
            """;

    private AutoAgentMonitoringPolicy() {
    }

    public static String append(String agentId, Stage stage, String prompt) {
        if (!GRAFANA_AGENT_ID.equals(agentId)) {
            return prompt;
        }

        return prompt + COMMON_POLICY + switch (stage) {
            case ANALYZER -> ANALYZER_POLICY;
            case EXECUTOR -> EXECUTOR_POLICY;
            case SUPERVISOR -> SUPERVISOR_POLICY;
            case SUMMARY -> SUMMARY_POLICY;
        };
    }

    public enum Stage {
        ANALYZER,
        EXECUTOR,
        SUPERVISOR,
        SUMMARY
    }
}
