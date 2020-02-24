package com.microsoft.sqlserver.msi;

public class NoMSIFoundException extends Exception {
    public  NoMSIFoundException() {
        super();
    }
    public NoMSIFoundException(String msg) {
        super(msg);
    }
}
