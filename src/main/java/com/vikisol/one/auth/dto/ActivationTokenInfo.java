package com.vikisol.one.auth.dto;

// Lets the frontend show "Welcome, {firstName}" (or an invalid/expired message) on the
// activation page before the employee has set a password - deliberately doesn't leak anything
// beyond first name.
public record ActivationTokenInfo(boolean valid, String firstName, String email) {
}
