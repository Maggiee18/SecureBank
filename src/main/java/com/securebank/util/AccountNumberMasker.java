package com.securebank.util;

public final class AccountNumberMasker {

    private static final int VISIBLE_DIGITS = 4;

    private AccountNumberMasker() {
    }

    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= VISIBLE_DIGITS) {
            return accountNumber;
        }
        int hidden = accountNumber.length() - VISIBLE_DIGITS;
        return "X".repeat(hidden) + accountNumber.substring(hidden);
    }
}
