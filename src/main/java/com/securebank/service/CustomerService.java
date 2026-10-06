package com.securebank.service;

import com.securebank.dto.customer.CustomerResponse;
import com.securebank.dto.customer.UpdateCustomerRequest;
import com.securebank.entity.AuditAction;
import com.securebank.entity.Customer;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method takes the authenticated customer's id, never an id from the request,
 * so one customer cannot read or change another's profile.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository, AuditService auditService) {
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public CustomerResponse getProfile(Long customerId) {
        return CustomerResponse.from(findCustomer(customerId));
    }

    @Transactional
    public CustomerResponse updateProfile(Long customerId, UpdateCustomerRequest request) {
        Customer customer = findCustomer(customerId);
        // Managed entity: Hibernate's dirty checking writes the change at commit, no save() needed.
        customer.updateProfile(request.fullName().trim(), request.phone());
        auditService.recordSuccess(customerId, AuditAction.PROFILE_UPDATED, "CUSTOMER",
                String.valueOf(customerId), "Profile updated");
        log.info("PROFILE_UPDATED customerId={}", customerId);
        return CustomerResponse.from(customer);
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", String.valueOf(customerId)));
    }
}
