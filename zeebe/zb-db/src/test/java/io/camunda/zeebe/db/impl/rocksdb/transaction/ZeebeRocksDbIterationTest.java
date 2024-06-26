/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;

public final class ZeebeRocksDbIterationTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();
  private ZeebeTransactionDb<DefaultColumnFamily> zeebeDb;
  private TransactionalColumnFamily<DefaultColumnFamily, DbCompositeKey<DbLong, DbLong>, DbNil>
      columnFamily;
  private DbLong firstKey;
  private DbLong secondKey;
  private DbCompositeKey<DbLong, DbLong> compositeKey;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = Mockito.spy(((ZeebeTransactionDb<DefaultColumnFamily>) dbFactory.createDb(pathName)));

    firstKey = new DbLong();
    secondKey = new DbLong();
    compositeKey = new DbCompositeKey<>(firstKey, secondKey);
    columnFamily =
        Mockito.spy(
            (TransactionalColumnFamily)
                zeebeDb.createColumnFamily(
                    DefaultColumnFamily.DEFAULT,
                    zeebeDb.createContext(),
                    compositeKey,
                    DbNil.INSTANCE));
  }

  @Test
  public void shouldStopIteratingAfterPrefixExceeded() {
    // given
    final AtomicReference<RocksIterator> spyIterator = new AtomicReference<>();
    Mockito.doAnswer(
            invocation -> {
              final Object spy = Mockito.spy(invocation.callRealMethod());
              spyIterator.set((RocksIterator) spy);
              return spy;
            })
        .when(columnFamily)
        .newIterator(Mockito.any(TransactionContext.class), Mockito.any(ReadOptions.class));

    final long prefixes = 3;
    final long suffixes = 5;

    for (long prefix = 0; prefix < prefixes; prefix++) {
      firstKey.wrapLong(prefix);
      for (long suffix = 0; suffix < suffixes; suffix++) {
        secondKey.wrapLong(suffix);
        columnFamily.upsert(compositeKey, DbNil.INSTANCE);
      }
    }

    // when
    firstKey.wrapLong(1);
    columnFamily.whileEqualPrefix(firstKey, ((key, value) -> {}));

    // then
    Mockito.verify(spyIterator.get(), Mockito.times((int) suffixes)).next();
  }
}
