package com.example.authservice.enums;

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

    public boolean isAtLeast(OrganizationRole required) {
        return rank >= required.rank;
    }
}
