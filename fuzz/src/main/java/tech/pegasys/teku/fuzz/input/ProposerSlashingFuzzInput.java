/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.fuzz.input;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class ProposerSlashingFuzzInput implements SimpleOffsetSerializable, SSZContainer {

  // TODO should this be a BeaconState or BeaconStateImpl?
  private BeaconStateImpl state;
  private ProposerSlashing proposer_slashing;

  public ProposerSlashingFuzzInput(
      final BeaconStateImpl state, final ProposerSlashing proposer_slashing) {
    this.state = state;
    this.proposer_slashing = proposer_slashing;
  }

  // NOTE: empty constructor is needed for reflection/introspection
  // public ProposerSlashingFuzzInput() {
  //  this(new BeaconStateImpl(), new ProposerSlashing());
  // }

  @Override
  public int getSSZFieldCount() {
    return state.getSSZFieldCount() + proposer_slashing.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.add(Bytes.EMPTY);
    fixedPartsList.add(SimpleOffsetSerializer.serialize(proposer_slashing));
    return fixedPartsList;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    return List.of(SimpleOffsetSerializer.serialize(state), Bytes.EMPTY);
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public ProposerSlashing getProposer_slashing() {
    return proposer_slashing;
  }

  public BeaconState getState() {
    return state;
  }
}
