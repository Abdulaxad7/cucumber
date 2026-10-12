package com.example.authservice.enums;

/**
 * Organization roles with an explicit rank (D3): OWNER 4, ADMIN 3, MEMBER 2, READER 1. Compare roles only with
 * {@link #isAtLeast(OrganizationRole)}; the declaration order carries no meaning.
 */
public enum OrganizationRole {
    OWNER(4),
    ADMIN(3),
    MEMBER(2),
    READER(1);

    private final int rank;

    OrganizationRole(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /** Whether this role's rank is at least the rank of {@code required}. */
    public boolean isAtLeast(OrganizationRole required) {
        return rank >= required.rank;
    }
}
