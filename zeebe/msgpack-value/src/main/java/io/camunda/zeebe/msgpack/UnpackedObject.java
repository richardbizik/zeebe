/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class UnpackedObject extends ObjectValue implements Recyclable, BufferReader, BufferWriter {

  protected final MsgPackReader reader = new MsgPackReader();
  protected final MsgPackWriter writer = new MsgPackWriter();

  /**
   * Creates a new UnpackedObject
   *
   * @param expectedDeclaredProperties a size hint for the number of declared properties. Providing
   *     the correct number helps to avoid allocations and memory copies.
   */
  public UnpackedObject(final int expectedDeclaredProperties) {
    super(expectedDeclaredProperties);
  }

  public void wrap(final DirectBuffer buff) {
    wrap(buff, 0, buff.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buff, final int offset, final int length) {
    reset();
    reader.wrap(buff, offset, length);
    try {
      read(reader);
    } catch (final Exception e) {
      throw new RuntimeException(
          "Could not deserialize object ["
              + getClass().getSimpleName()
              + "]. Deserialization stuck at offset "
              + reader.getOffset()
              + " of length "
              + length,
          e);
    }
  }

  @Override
  public int getLength() {
    return getEncodedLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    writer.wrap(buffer, offset);
    write(writer);
  }
}
