/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class TenantAttributeHolder {
  private static final String ATTRIBUTE_KEY = "io.camunda.zeebe.gateway.rest.tenantIds";

  private TenantAttributeHolder() {}

  public static List<String> tenantIds() {
    final var requestAttributes = RequestContextHolder.currentRequestAttributes();
    final var tenants =
        requestAttributes.getAttribute(ATTRIBUTE_KEY, RequestAttributes.SCOPE_REQUEST);
    final List<String> authorizedTenants;

    if (tenants != null) {
      authorizedTenants = (List<String>) tenants;
    } else {
      authorizedTenants = List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    return authorizedTenants;
  }

  public static void withTenantIds(final List<String> tenantIds) {
    final var requestAttributes = RequestContextHolder.currentRequestAttributes();
    requestAttributes.setAttribute(ATTRIBUTE_KEY, tenantIds, RequestAttributes.SCOPE_REQUEST);
  }
}
