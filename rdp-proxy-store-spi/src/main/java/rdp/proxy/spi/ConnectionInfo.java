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
    return "ConnectionInfo address=" + _address + " proxy=" + _proxy + " port=" + _port;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((_address == null) ? 0 : _address.hashCode());
    result = prime * result + _port;
    result = prime * result + ((_proxy == null) ? 0 : _proxy.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ConnectionInfo other = (ConnectionInfo) obj;
    if (_address == null) {
      if (other._address != null)
        return false;
    } else if (!_address.equals(other._address))
      return false;
    if (_port != other._port)
      return false;
    if (_proxy == null) {
      if (other._proxy != null)
        return false;
    } else if (!_proxy.equals(other._proxy))
      return false;
    return true;
  }

}
