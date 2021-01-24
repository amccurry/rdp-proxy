package rdp.proxy.server.relay;

import java.io.Closeable;
import java.net.Socket;
import java.util.Set;

import rdp.proxy.spi.ConnectionInfo;

public interface RdpClientConnectionEvents extends Closeable {

  public static RdpClientConnectionEvents NO_OP = new RdpClientConnectionEvents() {
  };

  default void newClientConnection(Socket socket) {

  }

  default void newClientConnectionConfigured(Socket socket) {

  }

  default void noFirstMessageFailure() {

  }

  default void readFirstMessage(BytesRef message) {

  }

  default void connectionInfo(Set<ConnectionInfo> connectionInfoSet) {

  }

  default void cookie(BytesRef message) {

  }

  default void noConnectionInfoFailure() {

  }

  default void createSession(String cookie, Closeable session) {

  }

  default void createRemoteConnection(Set<ConnectionInfo> connectionInfoSet) {

  }

  default void writeFirstMessage(BytesRef message) {

  }

  default void startInboundMessageRelay() {

  }

  default void startOutboundMessageRelay() {

  }

  @Override
  default void close() {

  }

}
