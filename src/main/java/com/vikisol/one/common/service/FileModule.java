package com.vikisol.one.common.service;

// Maps an HRMS module to its place in the Cloudinary folder hierarchy - see
// FileStorageService.resolveFolder() for exactly how each one is built. Adding a new module here
// is the only thing a future feature needs to do; nothing outside FileStorageService should ever
// construct a folder path by hand.
public enum FileModule {
    RECRUITMENT_CANDIDATE, // recruitment/candidates/{candidateId}/{documentType}/
    EMPLOYEE,              // employees/{employeeId}/{documentType}/
    PAYROLL,               // payroll/{documentType}/{year}/{month}/
    ATTENDANCE,            // attendance/{documentType}/
    LEAVE,                 // leave/{documentType}/
    ASSET,                 // assets/{documentType}/
    PROJECT,               // projects/{documentType}/
    TICKET,                // tickets/{documentType}/
    COMPANY                // company/{documentType}/
}
