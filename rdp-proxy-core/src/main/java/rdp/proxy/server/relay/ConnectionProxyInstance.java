package rdp.proxy.server.relay;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class ConnectionProxyInstance {

  String id;
  SocketInfo client;
  SocketInfo server;
  AtomicBoolean alive;

}
