package com.microsoft.sqlserver.msi;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

public class MsiAuthResponse {

    @SerializedName("access_token")
    private String accessToken;

    /**
     * The device code.
     */
    @SerializedName("resource")
    private String resource;

    /**
     * The verification url.
     */
    @SerializedName("token_type")
    private String tokenType;

    /**
     * The expiration time in seconds.
     */
    @SerializedName("expires_on")
    private String expiresOn;


    public String getAccessToken() {
        return accessToken;
    }

    public String getResource() {
        return resource;
    }
    public String getTokenType() {
        return tokenType;
    }

    public String getExpiresOn() {
        return expiresOn;
    }

}
