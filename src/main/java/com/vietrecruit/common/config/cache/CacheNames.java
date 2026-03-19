package com.vietrecruit.common.config.cache;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CacheNames {

    private static final String PREFIX = "vietrecruit:";

    // ── Job ──────────────────────────────────────────────────────────────
    /** Single published job detail: key = jobId */
    public static final String JOB_DETAIL = PREFIX + "job:detail";

    /** Paginated public job listings: manual RedisTemplate caching (not Spring Cache) */
    public static final String JOB_PUBLIC_LIST_PREFIX = PREFIX + "job:public:list:";

    // ── Category ─────────────────────────────────────────────────────────
    /** Single category: key = companyId::categoryId */
    public static final String CATEGORY_DETAIL = PREFIX + "category:detail";

    /** Category list per company: key = companyId */
    public static final String CATEGORY_LIST = PREFIX + "category:list";

    // ── Location ─────────────────────────────────────────────────────────
    /** Single location: key = companyId::locationId */
    public static final String LOCATION_DETAIL = PREFIX + "location:detail";

    /** Location list per company: key = companyId */
    public static final String LOCATION_LIST = PREFIX + "location:list";

    // ── Company ──────────────────────────────────────────────────────────
    /** Company profile: key = companyId */
    public static final String COMPANY_DETAIL = PREFIX + "company:detail";

    // ── Subscription Plan ────────────────────────────────────────────────
    /** All active plans list: key = "all" */
    public static final String PLAN_LIST = PREFIX + "plan:list";

    /** Single plan: key = planId */
    public static final String PLAN_DETAIL = PREFIX + "plan:detail";
}
