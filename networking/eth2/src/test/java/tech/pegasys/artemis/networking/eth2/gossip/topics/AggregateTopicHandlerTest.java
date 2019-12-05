/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class AggregateTopicHandlerTest {
  private final EventBus eventBus = spy(new EventBus());
  private final ChainStorageClient storageClient = new ChainStorageClient(eventBus);
  private final AggregateTopicHandler topicHandler =
      new AggregateTopicHandler(eventBus, storageClient);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(12, storageClient);

  @BeforeEach
  public void setup() {
    beaconChainUtil.initializeStorage();
  }

  @Test
  public void handleMessage_invalidAttestation_badState() throws Exception {
    final AggregateAndProof aggregate = DataStructureUtil.randomAggregateAndProof(1);
    final Bytes serialized = SimpleOffsetSerializer.serialize(aggregate);

    final boolean result = topicHandler.handleMessage(serialized);
    assertThat(result).isEqualTo(false);
    verify(eventBus, never()).post(aggregate);
  }
}
