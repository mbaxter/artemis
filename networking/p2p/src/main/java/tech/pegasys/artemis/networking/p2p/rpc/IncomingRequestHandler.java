package tech.pegasys.artemis.networking.p2p.rpc;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.peer.Peer;

public interface IncomingRequestHandler {

  void processBytes(Peer peer, RpcStream rpcStream, Bytes bytes);
}
