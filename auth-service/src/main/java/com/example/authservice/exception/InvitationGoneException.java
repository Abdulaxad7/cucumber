package com.example.authservice.exception;

public class InvitationGoneException extends ApiException {

    private InvitationGoneException(ErrorCode code, String detail) {
        super(code, detail);
    }

    public static InvitationGoneException alreadyAccepted() {
        return new InvitationGoneException(ErrorCode.INVITATION_ALREADY_ACCEPTED,
                "This invitation has already been accepted.");
    }

    public static InvitationGoneException revoked() {
        return new InvitationGoneException(ErrorCode.INVITATION_REVOKED, "This invitation has been revoked.");
    }

    public static InvitationGoneException expired() {
        return new InvitationGoneException(ErrorCode.INVITATION_EXPIRED, "This invitation has expired.");
    }
}
