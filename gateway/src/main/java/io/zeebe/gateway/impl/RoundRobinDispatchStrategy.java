/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl;

import io.zeebe.gateway.impl.clustering.ClientTopologyManager;
import io.zeebe.gateway.impl.clustering.ClusterStateImpl;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinDispatchStrategy implements RequestDispatchStrategy {

  protected final ClientTopologyManager topologyManager;
  protected AtomicInteger partitions = new AtomicInteger(0);

  public RoundRobinDispatchStrategy(final ClientTopologyManager topologyManager) {
    this.topologyManager = topologyManager;
  }

  @Override
  public int determinePartition() {
    final ClusterStateImpl topology = topologyManager.getTopology();

    final int offset = partitions.getAndIncrement();

    return topology.getPartition(offset);
  }
}
