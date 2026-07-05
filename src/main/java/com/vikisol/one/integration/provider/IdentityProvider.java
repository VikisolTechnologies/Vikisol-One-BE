package com.vikisol.one.integration.provider;

// Future work, NOT wired up yet: automated official-mailbox provisioning (Azure AD user creation
// + license assignment via Graph) so HR stops manually creating mailboxes in GoDaddy. Defined now
// so the interface exists for the abstraction layer, but there is no working implementation - see
// NoopIdentityProvider. Real implementation needs Azure AD app permissions (User.ReadWrite.All,
// Directory.ReadWrite.All) and a decision on which M365 license SKU to assign per new hire, which
// is a company-policy decision, not just an API call.
public interface IdentityProvider {

    String getProviderName();

    boolean isConfigured();

    /** Creates the official company mailbox/account for a new employee. */
    String createAccount(String officialEmail, String firstName, String lastName);

    void deactivateAccount(String officialEmail);
}
