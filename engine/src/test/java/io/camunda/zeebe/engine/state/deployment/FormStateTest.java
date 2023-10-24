/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.appliers.FormCreatedApplier;
import io.camunda.zeebe.engine.state.appliers.FormDeletedApplier;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class FormStateTest {

  private final String tenantId = "<default>";
  private MutableProcessingState processingState;
  private MutableFormState formState;
  private FormCreatedApplier formCreatedApplier;
  private FormDeletedApplier formDeletedApplier;

  @BeforeEach
  public void setup() {
    formState = processingState.getFormState();
    formCreatedApplier = new FormCreatedApplier(formState);
    formDeletedApplier = new FormDeletedApplier(formState);
  }

  @Test
  void shouldStoreForm() {
    // given
    final var formRecord = sampleFormRecord();
    formCreatedApplier.applyState(formRecord.getFormKey(), formRecord);

    // when
    final var maybePersistedForm = formState.findFormByKey(formRecord.getFormKey(), tenantId);

    // then
    assertThat(maybePersistedForm).isNotEmpty();
    final var persistedForm = maybePersistedForm.get();
    assertThat(bufferAsString(persistedForm.getFormId())).isEqualTo(formRecord.getFormId());
    assertThat(persistedForm.getVersion()).isEqualTo(formRecord.getVersion());
    assertThat(persistedForm.getFormKey()).isEqualTo(formRecord.getFormKey());
    assertThat(bufferAsString(persistedForm.getResourceName()))
        .isEqualTo(formRecord.getResourceName());
    assertThat(bufferAsArray(persistedForm.getChecksum())).isEqualTo(formRecord.getChecksum());
    assertThat(bufferAsArray(persistedForm.getResource())).isEqualTo(formRecord.getResource());
  }

  @Test
  void shouldFindLatestByFormId() {
    // given
    final var formRecord1 = sampleFormRecord();
    formCreatedApplier.applyState(formRecord1.getFormKey(), formRecord1);

    final var formRecord2 = sampleFormRecord(2, 2L);
    formCreatedApplier.applyState(formRecord2.getFormKey(), formRecord2);

    // when
    final var maybePersistedForm =
        formState.findLatestFormById(formRecord1.getFormIdBuffer(), tenantId);

    // then
    assertThat(maybePersistedForm).isNotEmpty();
    final var persistedForm = maybePersistedForm.get();
    assertThat(bufferAsString(persistedForm.getFormId())).isEqualTo(formRecord2.getFormId());
    assertThat(persistedForm.getVersion()).isEqualTo(2);
    assertThat(persistedForm.getFormKey()).isEqualTo(formRecord2.getFormKey());
    assertThat(bufferAsString(persistedForm.getResourceName()))
        .isEqualTo(formRecord2.getResourceName());
    assertThat(bufferAsArray(persistedForm.getChecksum())).isEqualTo(formRecord2.getChecksum());
    assertThat(bufferAsArray(persistedForm.getResource())).isEqualTo(formRecord2.getResource());
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormId() {
    // when
    final var persistedForm = formState.findLatestFormById(wrapString("form-1"), tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormKey() {
    // when
    final var persistedForm = formState.findFormByKey(1L, tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }

  @Test
  void shouldNotFindFormAfterDeletion() {
    // given
    final var form = sampleFormRecord();
    formCreatedApplier.applyState(form.getFormKey(), form);

    assertThat(formState.findLatestFormById(form.getFormIdBuffer(), form.getTenantId()))
        .isNotEmpty();

    // when
    formDeletedApplier.applyState(form.getFormKey(), form);

    // then
    assertThat(formState.findLatestFormById(form.getFormIdBuffer(), form.getTenantId())).isEmpty();
  }

  @Test
  void shouldNotFindVersion2AsLatestFormAfterDeletion() {
    // given
    final var formV1 = sampleFormRecord();
    final var formV2 = sampleFormRecord(2, 2L);
    formCreatedApplier.applyState(formV1.getFormKey(), formV1);
    formCreatedApplier.applyState(formV2.getFormKey(), formV2);

    final var latestFormOpt =
        formState.findLatestFormById(formV2.getFormIdBuffer(), formV2.getTenantId());
    assertThat(latestFormOpt).isNotEmpty();
    assertThat(latestFormOpt.get().getVersion()).isEqualTo(2);

    // when
    formDeletedApplier.applyState(formV2.getFormKey(), formV2);

    // then
    final var latestFormV1Opt =
        formState.findLatestFormById(formV2.getFormIdBuffer(), formV2.getTenantId());
    assertThat(latestFormV1Opt).isNotEmpty();
    assertThat(latestFormV1Opt.get().getVersion()).isEqualTo(1);
  }

  @Test
  void shouldFindVersion2AsLatestFormAfterDeletion() {
    // given
    final var formV1 = sampleFormRecord();
    final var formV2 = sampleFormRecord(2, 2L);
    formCreatedApplier.applyState(formV1.getFormKey(), formV1);
    formCreatedApplier.applyState(formV2.getFormKey(), formV2);

    final var latestFormOpt =
        formState.findLatestFormById(formV2.getFormIdBuffer(), formV2.getTenantId());
    assertThat(latestFormOpt).isNotEmpty();
    assertThat(latestFormOpt.get().getVersion()).isEqualTo(2);

    // when
    formDeletedApplier.applyState(formV1.getFormKey(), formV1);

    // then
    final var latestFormV2Opt =
        formState.findLatestFormById(formV2.getFormIdBuffer(), formV2.getTenantId());
    assertThat(latestFormV2Opt).isNotEmpty();
    assertThat(latestFormV2Opt.get().getVersion()).isEqualTo(2);
  }

  @Test
  void shouldNotReuseADeletedVersionNumber() {
    // given
    final var form = sampleFormRecord();
    formCreatedApplier.applyState(form.getFormKey(), form);
    final var formV2 = sampleFormRecord(2, 2L);
    formCreatedApplier.applyState(formV2.getFormKey(), formV2);

    // when
    formDeletedApplier.applyState(form.getFormKey(), form);
    formDeletedApplier.applyState(formV2.getFormKey(), formV2);

    // then
    final var nextAvailableVersion =
        formState.getNextFormVersion(form.getFormId(), form.getTenantId());
    assertThat(nextAvailableVersion).isEqualTo(3);
  }

  private FormRecord sampleFormRecord(final int version, final long key) {
    return new FormRecord()
        .setFormId("form-id")
        .setVersion(version)
        .setFormKey(key)
        .setResourceName("form-1.form")
        .setChecksum(wrapString("checksum"))
        .setResource(wrapString("form-resource"))
        .setTenantId(tenantId);
  }

  private FormRecord sampleFormRecord() {
    return sampleFormRecord(1, 1L);
  }
}
