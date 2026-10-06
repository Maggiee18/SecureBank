package com.securebank.exception;

public class ResourceNotFoundException extends BankingException {

    public ResourceNotFoundException(String resource, String identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found: " + identifier);
    }
}
