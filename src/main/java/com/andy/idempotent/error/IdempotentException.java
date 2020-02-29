package com.andy.idempotent.error;

/**
 * 幂等组件异常
 * @author andy
 *
 */
public class IdempotentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private String errorCode;
    private String errorMessage;

    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public IdempotentException() {
        super();
    }
    
    public IdempotentException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public IdempotentException(String errorMessage) {
        super(errorMessage);
        this.errorMessage = errorMessage;
    }

    public IdempotentException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    protected IdempotentException(String errorCode, String errorMessage, Throwable cause,
            boolean enableSuppression,
            boolean writableStackTrace) {
        super(errorMessage, cause, enableSuppression, writableStackTrace);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
