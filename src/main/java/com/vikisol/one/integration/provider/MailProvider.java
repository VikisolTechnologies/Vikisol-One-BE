package com.vikisol.one.integration.provider;

public interface MailProvider {

    String getProviderName();

    boolean isConfigured();

    void sendMail(MailMessage message);
}
