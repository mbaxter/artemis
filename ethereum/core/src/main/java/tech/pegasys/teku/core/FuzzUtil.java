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

package tech.pegasys.teku.core;

import com.google.common.primitives.UnsignedLong;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.sos.ReflectionInformation;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.config.Constants;

// TODO a Java FuzzHarness interface? - that way type safety can be checked at compile time
// JNI removes type safety
public class FuzzUtil {
  // TODO set config as initialization param here? see
  // util/src/main/java/tech/pegasys/artemis/util/config/Constants.java setConstants
  // though is a global setting so kinda weird to allow that side-effect within a constructor
  // a static "initialize" function could make more sense, but doesn't set a requirement that it is
  // called before any
  // fuzzing harness
  //
  // Could also have these all in separate classes, which implement a "FuzzHarness" interface

  // Size of ValidatorIndex returned by shuffle
  // private static final int VALIDATOR_INDEX_BYTES = Integer.BYTES;
  private static final int OUTPUT_INDEX_BYTES = Long.BYTES;

  private boolean disable_bls;

  // NOTE: this uses primitive values as parameters to more easily call via JNI
  public FuzzUtil(final boolean useMainnetConfig, final boolean disable_bls) {
    // NOTE: makes global Constants/config changes
    if (useMainnetConfig) {
      Constants.setConstants("mainnet");
    } else {
      Constants.setConstants("minimal");
    }
    // guessing this might be necessary soon?
    SimpleOffsetSerializer.setConstants();
    SimpleOffsetSerializer.classReflectionInfo.put(
        AttestationFuzzInput.class, new ReflectionInformation(AttestationFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        AttesterSlashingFuzzInput.class,
        new ReflectionInformation(AttesterSlashingFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        BlockFuzzInput.class, new ReflectionInformation(BlockFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        BlockHeaderFuzzInput.class, new ReflectionInformation(BlockHeaderFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        DepositFuzzInput.class, new ReflectionInformation(DepositFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        ProposerSlashingFuzzInput.class,
        new ReflectionInformation(ProposerSlashingFuzzInput.class));
    SimpleOffsetSerializer.classReflectionInfo.put(
        VoluntaryExitFuzzInput.class, new ReflectionInformation(VoluntaryExitFuzzInput.class));

    this.disable_bls = disable_bls;
    /*if (disable_bls) {
      // TODO enable/disable BLS verification
      // TODO implement
    }*/
  }

  public Optional<byte[]> fuzzAttestation(final byte[] input) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error
    AttestationFuzzInput structuredInput =
        SimpleOffsetSerializer.deserialize(Bytes.wrap(input), AttestationFuzzInput.class);
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_attestations(
                        state, SSZList.singleton(structuredInput.getAttestation()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzAttesterSlashing(final byte[] input) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error
    AttesterSlashingFuzzInput structuredInput =
        SimpleOffsetSerializer.deserialize(Bytes.wrap(input), AttesterSlashingFuzzInput.class);
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_attester_slashings(
                        state, SSZList.singleton(structuredInput.getAttester_slashing()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzBlock(final byte[] input) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error
    BlockFuzzInput structuredInput =
        SimpleOffsetSerializer.deserialize(Bytes.wrap(input), BlockFuzzInput.class);
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    // TODO this currently will disable state root validation and some (not all) sig validation,
    // would be preferable to control each individually
    // this currently causes some blocks differences to be detected
    boolean validate_root_and_sigs = !disable_bls;
    try {
      StateTransition transition = new StateTransition();
      BeaconState postState =
          transition.initiate(
              structuredInput.getState(),
              structuredInput.getSigned_block(),
              validate_root_and_sigs);
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (StateTransitionException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzBlockHeader(final byte[] input) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error
    BlockHeaderFuzzInput structuredInput =
        SimpleOffsetSerializer.deserialize(Bytes.wrap(input), BlockHeaderFuzzInput.class);
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_block_header(state, structuredInput.getBlock());
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzDeposit(final byte[] input) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error

    DepositFuzzInput structuredInput =
        SimpleOffsetSerializer.deserialize(Bytes.wrap(input), DepositFuzzInput.class);
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    // TODO confirm deposit is a fixed size container
    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_deposits(
                        state, SSZList.singleton(structuredInput.getDeposit()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzProposerSlashing(final byte[] input) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error
    ProposerSlashingFuzzInput structuredInput =
        SimpleOffsetSerializer.deserialize(Bytes.wrap(input), ProposerSlashingFuzzInput.class);
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_proposer_slashings(
                        state, SSZList.singleton(structuredInput.getProposer_slashing()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  public Optional<byte[]> fuzzShuffle(final byte[] input) {
    if (input.length < (32 + 2)) {
      return Optional.empty();
    }
    // Mask it to make ensure positive before using remainder.
    int count = ((int) (0xFFFFFFFFL & BeaconStateUtil.bytes_to_int(Bytes.wrap(input, 0, 2)))) % 100;

    Bytes32 seed = Bytes32.wrap(input, 2);

    // NOTE: could use the following, but that is not used by the current implementation
    // int[] shuffled = BeaconStateUtil.shuffle(count, seed);
    // TODO shuffle returns an int (int32), but should be uint64 == (java long is int64)
    // so does this break if validator indexes are negative integers?
    // use a google UnsignedLong?
    // anything weird with signedness here?
    // any risk here? - not for this particular fuzzing as we only count <= 100

    // NOTE: although compute_shuffled_index returns an int, we save as a long for consistency
    ByteBuffer result_bb = ByteBuffer.allocate(count * OUTPUT_INDEX_BYTES);
    // Convert to little endian bytes
    result_bb.order(ByteOrder.LITTLE_ENDIAN);

    for (int i = 0; i < count; i++) {
      result_bb.putLong(CommitteeUtil.compute_shuffled_index(i, count, seed));
    }
    return Optional.of(result_bb.array());
  }

  public Optional<byte[]> fuzzVoluntaryExit(final byte[] input) {
    // allow exception to propagate on failure - indicates a preprocessing or deserializing error
    VoluntaryExitFuzzInput structuredInput =
        SimpleOffsetSerializer.deserialize(Bytes.wrap(input), VoluntaryExitFuzzInput.class);
    if (structuredInput == null) {
      throw new RuntimeException(
          "Failed to deserialize input. Likely a preprocessing or deserialization bug.");
    }
    // TODO confirm exit is a fixed container
    // process and return post state
    try {
      BeaconState postState =
          structuredInput
              .getState()
              .updated(
                  state -> {
                    BlockProcessorUtil.process_voluntary_exits(
                        state, SSZList.singleton(structuredInput.getExit()));
                  });
      Bytes output = SimpleOffsetSerializer.serialize(postState);
      return Optional.of(output.toArrayUnsafe());
    } catch (BlockProcessingException e) {
      // "expected error"
      return Optional.empty();
    }
  }

  /** ******************** Input Classes ********************* */

  // TODO common abstract class for all operations that are state + op?
  // TODO move to separate package?
  // NOTE: not obvious how to have a generic "OperationFuzzInput" class because the get_fixed_parts
  // and get_variable_parts
  // implementations can be different
  public static class AttestationFuzzInput implements SimpleOffsetSerializable, SSZContainer {

    // TODO should this be a BeaconState or BeaconStateImpl?
    private BeaconStateImpl state;
    private Attestation attestation;

    public AttestationFuzzInput(final BeaconStateImpl state, final Attestation attestation) {
      this.state = state;
      this.attestation = attestation;
    }

    // NOTE: empty constructor is needed for reflection/introspection
    public AttestationFuzzInput() {
      this(new BeaconStateImpl(), new Attestation());
    }

    @Override
    public int getSSZFieldCount() {
      return state.getSSZFieldCount() + attestation.getSSZFieldCount();
    }

    // Since its both fields are variable we leave untouched?
    /*@Override
    public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(state.get_fixed_parts());
    fixedPartsList.addAll(attestation.get_fixed_parts());
    return fixedPartsList;
    }*/

    @Override
    public List<Bytes> get_variable_parts() {
      // Because we know both fields are variable and registered, we can just serialize.
      return List.of(
          SimpleOffsetSerializer.serialize(state), SimpleOffsetSerializer.serialize(attestation));
    }

    /** ******************* * GETTERS & SETTERS * * ******************* */
    public Attestation getAttestation() {
      return attestation;
    }

    public BeaconState getState() {
      return state;
    }
  }

  public static class AttesterSlashingFuzzInput implements SimpleOffsetSerializable, SSZContainer {

    // TODO should this be a BeaconState or BeaconStateImpl?
    private BeaconStateImpl state;
    private AttesterSlashing attester_slashing;

    public AttesterSlashingFuzzInput(
        final BeaconStateImpl state, final AttesterSlashing attester_slashing) {
      this.state = state;
      this.attester_slashing = attester_slashing;
    }

    // NOTE: empty constructor is needed for reflection/introspection
    // public AttesterSlashingFuzzInput() {
    //  this(new BeaconStateImpl(), new AttesterSlashing());
    // }

    @Override
    public int getSSZFieldCount() {
      return state.getSSZFieldCount() + attester_slashing.getSSZFieldCount();
    }

    // Since its both fields are variable we leave untouched?
    /*@Override
    public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(state.get_fixed_parts());
    fixedPartsList.addAll(attester_slashing.get_fixed_parts());
    return fixedPartsList;
    }*/

    @Override
    public List<Bytes> get_variable_parts() {
      // Because we know both fields are variable and registered, we can just serialize.
      return List.of(
          SimpleOffsetSerializer.serialize(state),
          SimpleOffsetSerializer.serialize(attester_slashing));
    }

    /** ******************* * GETTERS & SETTERS * * ******************* */
    public AttesterSlashing getAttester_slashing() {
      return attester_slashing;
    }

    public BeaconState getState() {
      return state;
    }
  }

  public static class BlockFuzzInput implements SimpleOffsetSerializable, SSZContainer {

    private BeaconStateImpl state;
    private SignedBeaconBlock signed_block;

    public BlockFuzzInput(final BeaconStateImpl state, final SignedBeaconBlock signed_block) {
      this.state = state;
      this.signed_block = signed_block;
    }

    // NOTE: empty constructor is needed for reflection/introspection
    public BlockFuzzInput() {
      this(new BeaconStateImpl(), new SignedBeaconBlock(new BeaconBlock(), BLSSignature.empty()));
    }

    @Override
    public int getSSZFieldCount() {
      return state.getSSZFieldCount() + signed_block.getSSZFieldCount();
    }

    // Since its both fields are variable we leave untouched?
    /*@Override
    public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(state.get_fixed_parts());
    fixedPartsList.addAll(signed_block.get_fixed_parts());
    return fixedPartsList;
    }*/

    @Override
    public List<Bytes> get_variable_parts() {
      // Because we know both fields are variable and registered, we can just serialize.
      return List.of(
          SimpleOffsetSerializer.serialize(state), SimpleOffsetSerializer.serialize(signed_block));
    }

    /** ******************* * GETTERS & SETTERS * * ******************* */
    public SignedBeaconBlock getSigned_block() {
      return signed_block;
    }

    public BeaconState getState() {
      return state;
    }
  }

  /**
   * Note: BlockHeader fuzzing target accepts a block as input (not a SignedBeaconBlock or
   * BeaconBlockHeader)
   */
  public static class BlockHeaderFuzzInput implements SimpleOffsetSerializable, SSZContainer {

    // TODO should this be a BeaconState or BeaconStateImpl?
    private BeaconStateImpl state;
    private BeaconBlock block;

    public BlockHeaderFuzzInput(final BeaconStateImpl state, final BeaconBlock block) {
      this.state = state;
      this.block = block;
    }

    // NOTE: empty constructor is needed for reflection/introspection
    public BlockHeaderFuzzInput() {
      this(new BeaconStateImpl(), new BeaconBlock());
    }

    @Override
    public int getSSZFieldCount() {
      return state.getSSZFieldCount() + block.getSSZFieldCount();
    }

    // Since its both fields are variable we leave untouched?
    /*@Override
    public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(state.get_fixed_parts());
    fixedPartsList.addAll(block.get_fixed_parts());
    return fixedPartsList;
    }*/

    @Override
    public List<Bytes> get_variable_parts() {
      // Because we know both fields are variable and registered, we can just serialize.
      return List.of(
          SimpleOffsetSerializer.serialize(state), SimpleOffsetSerializer.serialize(block));
    }

    /** ******************* * GETTERS & SETTERS * * ******************* */
    public BeaconBlock getBlock() {
      return block;
    }

    public BeaconState getState() {
      return state;
    }
  }

  public static class DepositFuzzInput implements SimpleOffsetSerializable, SSZContainer {

    // TODO should this be a BeaconState or BeaconStateImpl?
    private BeaconStateImpl state;
    private Deposit deposit;

    public DepositFuzzInput(final BeaconStateImpl state, final Deposit deposit) {
      this.state = state;
      this.deposit = deposit;
    }

    // NOTE: empty constructor is needed for reflection/introspection
    public DepositFuzzInput() {
      this(new BeaconStateImpl(), new Deposit());
    }

    @Override
    public int getSSZFieldCount() {
      return state.getSSZFieldCount() + deposit.getSSZFieldCount();
    }

    @Override
    public List<Bytes> get_fixed_parts() {
      List<Bytes> fixedPartsList = new ArrayList<>();
      fixedPartsList.add(Bytes.EMPTY);
      fixedPartsList.add(SimpleOffsetSerializer.serialize(deposit));
      return fixedPartsList;
    }

    @Override
    public List<Bytes> get_variable_parts() {
      return List.of(SimpleOffsetSerializer.serialize(state), Bytes.EMPTY);
    }

    /** ******************* * GETTERS & SETTERS * * ******************* */
    public Deposit getDeposit() {
      return deposit;
    }

    public BeaconState getState() {
      return state;
    }
  }

  public static class ProposerSlashingFuzzInput implements SimpleOffsetSerializable, SSZContainer {

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

  public static class VoluntaryExitFuzzInput implements SimpleOffsetSerializable, SSZContainer {

    // TODO should this be a BeaconState or BeaconStateImpl?
    private BeaconStateImpl state;
    private SignedVoluntaryExit exit;

    public VoluntaryExitFuzzInput(final BeaconStateImpl state, final SignedVoluntaryExit exit) {
      this.state = state;
      this.exit = exit;
    }

    // NOTE: empty constructor is needed for reflection/introspection
    // TODO how is new ReflectionInformation(VoluntaryExit.class) supposed to work when
    // it cals newInstance() with no arguments but VoluntaryExit has no 0 arg constructor?
    public VoluntaryExitFuzzInput() {
      this(
          new BeaconStateImpl(),
          new SignedVoluntaryExit(
              new VoluntaryExit(UnsignedLong.valueOf(0), UnsignedLong.valueOf(0)),
              BLSSignature.empty()));
    }

    @Override
    public int getSSZFieldCount() {
      return state.getSSZFieldCount() + exit.getSSZFieldCount();
    }

    @Override
    public List<Bytes> get_fixed_parts() {
      List<Bytes> fixedPartsList = new ArrayList<>();
      fixedPartsList.add(Bytes.EMPTY);
      fixedPartsList.add(SimpleOffsetSerializer.serialize(exit));
      return fixedPartsList;
    }

    @Override
    public List<Bytes> get_variable_parts() {
      return List.of(SimpleOffsetSerializer.serialize(state), Bytes.EMPTY);
    }

    /** ******************* * GETTERS & SETTERS * * ******************* */
    public SignedVoluntaryExit getExit() {
      return exit;
    }

    public BeaconState getState() {
      return state;
    }
  }
}
