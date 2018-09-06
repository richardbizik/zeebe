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
package io.zeebe.gateway.impl.subscription.topic;

import io.zeebe.gateway.api.subscription.RecordHandler;
import io.zeebe.gateway.api.subscription.TopicSubscription;
import io.zeebe.gateway.impl.Loggers;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.gateway.impl.record.UntypedRecordImpl;
import io.zeebe.gateway.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.gateway.impl.subscription.SubscriberGroup;
import io.zeebe.gateway.impl.subscription.SubscriptionManager;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class TopicSubscriberGroup extends SubscriberGroup<TopicSubscriber>
    implements TopicSubscription {
  private final AtomicBoolean processingFlag = new AtomicBoolean(false);
  private final TopicSubscriptionSpec subscription;

  public TopicSubscriberGroup(
      final ActorControl actor,
      final ZeebeClientImpl client,
      final SubscriptionManager acquisition,
      final TopicSubscriptionSpec subscription) {
    super(actor, client, acquisition);
    this.subscription = subscription;
  }

  @Override
  public int poll() {
    return pollEvents(subscription.getHandler());
  }

  public int poll(final RecordHandler recordHandler) {
    return pollEvents((e) -> recordHandler.onRecord(e));
  }

  @Override
  public int pollEvents(final CheckedConsumer<UntypedRecordImpl> pollHandler) {

    // ensuring at most one thread polls at a time which is the guarantee we give for subscriptions
    if (processingFlag.compareAndSet(false, true)) {
      try {
        return super.pollEvents(pollHandler);
      } finally {
        processingFlag.set(false);
      }
    } else {
      return 0;
    }
  }

  @Override
  protected ActorFuture<? extends EventSubscriptionCreationResult> requestNewSubscriber(
      final int partitionId) {
    return new CreateTopicSubscriptionCommandImpl(client.getCommandManager(), partitionId)
        .startPosition(subscription.getStartPosition(partitionId))
        .bufferSize(subscription.getBufferSize())
        .name(subscription.getName())
        .forceStart(subscription.isForceStart())
        .send();
  }

  @Override
  protected TopicSubscriber buildSubscriber(final EventSubscriptionCreationResult result) {
    return new TopicSubscriber(
        client,
        subscription,
        result.getSubscriberKey(),
        result.getEventPublisher(),
        result.getPartitionId(),
        this,
        subscriptionManager);
  }

  @Override
  protected ActorFuture<Void> doCloseSubscriber(final TopicSubscriber subscriber) {
    final ActorFuture<?> ackFuture = subscriber.acknowledgeLastProcessedEvent();

    final CompletableActorFuture<Void> closeFuture = new CompletableActorFuture<>();
    actor.runOnCompletionBlockingCurrentPhase(
        ackFuture,
        (ackResult, ackThrowable) -> {
          if (ackThrowable != null) {
            Loggers.SUBSCRIPTION_LOGGER.error(
                "Could not acknowledge last event position before closing subscriber. Ignoring.",
                ackThrowable);
          }

          final ActorFuture<Void> closeRequestFuture = subscriber.requestSubscriptionClose();
          actor.runOnCompletionBlockingCurrentPhase(
              closeRequestFuture,
              (closeResult, closeThrowable) -> {
                if (closeThrowable == null) {
                  closeFuture.complete(closeResult);
                } else {
                  closeFuture.completeExceptionally(closeThrowable);
                }
              });
        });
    return closeFuture;
  }

  @Override
  protected String describeGroup() {
    return subscription.toString();
  }
}
