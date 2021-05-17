package com.andy.idempotent.error;

/**
 * @author andy
 */
public enum CommonErrorEnum {

    SYS_ERROR("500001", "System error, please contact administrator."),
    NO_HANDLER_ERROR("500002", "The service you requested does not exist."),
    IDEMPOTENT_REQUEST_EXIST("500003", "Please do not repeat the request."),
    SAVE_IDEMPONTENT_REQUEST_FAIL("500004", "Save idempontent request fail."),
    ;

    private String message;
    private String code;

    private CommonErrorEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

}
