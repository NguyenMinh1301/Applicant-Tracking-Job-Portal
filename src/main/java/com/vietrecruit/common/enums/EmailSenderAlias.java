package com.vietrecruit.common.enums;

import lombok.Getter;

@Getter
public enum EmailSenderAlias {
    // spotless:off
    NO_REPLY("VietRecruit", "no-reply"),
    AUTHENTICATION("VietRecruit Auth", "authentication"),
    NOTIFICATION("VietRecruit", "notification");
    // spotless:on

    private final String displayName;
    private final String prefix;

    EmailSenderAlias(String displayName, String prefix) {
        this.displayName = displayName;
        this.prefix = prefix;
    }

    /**
     * Constructs an RFC 5322 compliant From address.
     *
     * @param domain the verified sending domain (e.g., "vietrecruit.site")
     * @return formatted address like "VietRecruit <no-reply@vietrecruit.site>"
     */
    public String toFromAddress(String domain) {
        return displayName + " <" + prefix + "@" + domain + ">";
    }
}
