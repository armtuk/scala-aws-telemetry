package com.plexq.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

import java.io.IOException;

public class CredentialHelper {
    private static AWSCredentials creds = DefaultAWSCredentialsProviderChain.getInstance().getCredentials();

    public static AWSCredentials buildCredentials() throws IOException {
        return creds;
    }
}
