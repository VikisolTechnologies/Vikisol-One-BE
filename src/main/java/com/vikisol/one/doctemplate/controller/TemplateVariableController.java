package com.vikisol.one.doctemplate.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.doctemplate.dto.TemplateVariableRequest;
import com.vikisol.one.doctemplate.entity.TemplateVariable;
import com.vikisol.one.doctemplate.service.TemplateVariableService;
import com.vikisol.one.document.entity.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/template-variables")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
public class TemplateVariableController {

    private final TemplateVariableService variableService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TemplateVariable>>> listAvailableFor(@RequestParam(required = false) Document.DocumentType documentType) {
        List<TemplateVariable> variables = documentType != null
                ? variableService.listAvailableFor(documentType)
                : variableService.listAll();
        return ResponseEntity.ok(new ApiResponse<>(true, "Variables retrieved", variables));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemplateVariable>> create(@RequestBody TemplateVariableRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Variable created", variableService.create(request)));
    }
}
