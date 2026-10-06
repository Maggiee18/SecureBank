package com.securebank.controller;

import com.securebank.config.OpenApiConfig;
import com.securebank.dto.customer.CustomerResponse;
import com.securebank.dto.customer.UpdateCustomerRequest;
import com.securebank.security.AuthenticatedCustomer;
import com.securebank.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer", description = "The logged-in customer's own profile")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get my profile")
    public CustomerResponse getMyProfile(@AuthenticationPrincipal AuthenticatedCustomer principal) {
        return customerService.getProfile(principal.id());
    }

    @PutMapping("/me")
    @Operation(summary = "Update my name and phone number")
    public CustomerResponse updateMyProfile(@AuthenticationPrincipal AuthenticatedCustomer principal,
                                            @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.updateProfile(principal.id(), request);
    }
}
