package com.example.authservice.enums;

public enum ProjectRole {
    ADMIN(4),
    MEMBER(3),
    TESTER(2),
    READER(1);

    private final int rank;

    ProjectRole(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public boolean isAtLeast(ProjectRole required) {
        return rank >= required.rank;
    }
}
