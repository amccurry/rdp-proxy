package rdp.proxy.server.relay;

public class BytesRef {

  byte[] buffer = new byte[1000];
  int length;

  void growSetLength(int newLength) {
    length = newLength;
    if (newLength <= buffer.length) {
      return;
    }
    byte[] newBuffer = new byte[newLength];
    System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
    buffer = newBuffer;
  }

}
