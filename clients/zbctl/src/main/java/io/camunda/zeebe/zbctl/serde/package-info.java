@Json.Import(Topology.class)
@Json.Import(BrokerInfo.class)
@Json.Import(PartitionInfo.class)
@Json.Import(PublishMessageResponse.class)
package io.camunda.zeebe.zbctl.serde;

import io.avaje.jsonb.Json;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.response.Topology;