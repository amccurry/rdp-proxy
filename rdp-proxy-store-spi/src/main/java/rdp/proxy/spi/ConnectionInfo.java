package rdp.proxy.spi;

import java.net.InetAddress;
import java.net.Proxy;

public class ConnectionInfo {

  private final InetAddress _address;
  private final Proxy _proxy;
  private final int _port;

  public ConnectionInfo(Proxy proxy, InetAddress address, int port) {
    _proxy = proxy;
    _address = address;
    _port = port;
  }

  public ConnectionInfo(InetAddress address, int port) {
    _proxy = Proxy.NO_PROXY;
    _address = address;
    _port = port;
  }

  public InetAddress getAddress() {
    return _address;
  }

  public Proxy getProxy() {
    return _proxy;
  }

  public int getPort() {
    return _port;
  }

  @Override
  public String toString() {
    return "ConnectionInfo [_address=" + _address + ", _proxy=" + _proxy + ", _port=" + _port + "]";
  }

}
