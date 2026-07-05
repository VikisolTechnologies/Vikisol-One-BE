package com.vikisol.one.integration.provider;

import org.springframework.stereotype.Component;

// Default IdentityProvider until Microsoft 365 (or another provider) actually implements
// automated mailbox creation - HR still creates the official mailbox manually (currently in
// GoDaddy) and enters the resulting address into the Employee form, same as today.
@Component
public class NoopIdentityProvider implements IdentityProvider {

    @Override
    public String getProviderName() {
        return "None (manual mailbox creation)";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String createAccount(String officialEmail, String firstName, String lastName) {
        throw new UnsupportedOperationException("Automated mailbox creation is not yet implemented - create the mailbox manually and enter the official email on the employee form.");
    }

    @Override
    public void deactivateAccount(String officialEmail) {
        throw new UnsupportedOperationException("Automated mailbox deactivation is not yet implemented.");
    }
}
