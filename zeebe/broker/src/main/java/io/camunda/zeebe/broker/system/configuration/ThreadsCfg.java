/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

public final class ThreadsCfg implements ConfigurationEntry {
  private int cpuThreadCount = 2;
  private int ioThreadCount = 2;

  public int getCpuThreadCount() {
    return cpuThreadCount;
  }

  public void setCpuThreadCount(final int cpuThreads) {
    cpuThreadCount = cpuThreads;
  }

  public int getIoThreadCount() {
    return ioThreadCount;
  }

  public void setIoThreadCount(final int ioThreads) {
    ioThreadCount = ioThreads;
  }

  @Override
  public String toString() {
    return "ThreadsCfg{"
        + "cpuThreadCount="
        + cpuThreadCount
        + ", ioThreadCount="
        + ioThreadCount
        + '}';
  }
}
