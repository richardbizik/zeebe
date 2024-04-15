/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public final class ProblemAuthFailureHandler
    implements AuthenticationFailureHandler, AccessDeniedHandler, AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Autowired
  public ProblemAuthFailureHandler(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void onAuthenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException error)
      throws IOException, ServletException {
    handleFailure(request, response, HttpStatus.UNAUTHORIZED, error);
  }

  @Override
  public void handle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AccessDeniedException error)
      throws IOException, ServletException {
    // if a token was passed but could not be validated, onAuthenticationFailure is called
    // however, if no token was passed, then access is denied here, and we want to distinguish
    // between unauthorized and forbidden; we can do that by checking the session principal to see
    // if it's authenticated or not
    final var principal = request.getUserPrincipal();
    if (principal instanceof final Authentication auth && auth.isAuthenticated()) {
      handleFailure(request, response, HttpStatus.FORBIDDEN, error);
    }

    handleFailure(request, response, HttpStatus.UNAUTHORIZED, error);
  }

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException error)
      throws IOException, ServletException {
    handleFailure(request, response, HttpStatus.UNAUTHORIZED, error);
  }

  private void handleFailure(
      HttpServletRequest request,
      HttpServletResponse response,
      final HttpStatus status,
      final Exception error)
      throws IOException {
    final var problem = ProblemDetail.forStatus(status);
    problem.setDetail(error.getMessage());
    final var problemJSON = objectMapper.writeValueAsString(problem);

    response.reset();
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().append(problemJSON);
  }
}
