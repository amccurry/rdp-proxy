package rdp.proxy.server.relay;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageUtil {

  private final static char[] HES_ARRAY = "0123456789ABCDEF".toCharArray();

  public static BytesRef readFirstMessage(InputStream input, BytesRef bytesRef) throws IOException {
    int tpktVersion = input.read();
    if (tpktVersion == -1) {
      return null;
    }
    if (tpktVersion != 3) {
      log.error("Unknown client, hang up");
      return null;
    }
    return readMessageType1(input, tpktVersion, bytesRef);
  }

  public static BytesRef readMessage(InputStream input, BytesRef bytesRef) throws IOException {
    int b1 = input.read();
    switch (b1) {
    case -1:
      // socket closed
      return null;

    case 3:
      return readMessageType1(input, b1, bytesRef);

    case 20:
    case 21:
    case 22:
    case 23:
      return readMessageType2(input, b1, bytesRef);

    case 96:
    case 132:
    case 176:
    case 184:
      return readMessageType3(input, b1, bytesRef);

    default:
      break;
    }

    int available = input.available();
    byte[] buf = new byte[available + 1];
    buf[0] = (byte) b1;
    readFully(input, buf, 1, available);
    log.error("Unknown message, available {} msg {}", available, bytesToHex(buf));

    return null;
  }

  public static BytesRef readMessageType1(InputStream input, int b1, BytesRef bytesRef) throws IOException {
    int r1 = input.read();
    int len = readUnsignedShort(input);
    if (!isValidLength(len)) {
      return null;
    }

    bytesRef.growSetLength(len);
    readFully(input, bytesRef.buffer, 4, len - 4);
    bytesRef.buffer[0] = (byte) b1;
    bytesRef.buffer[1] = (byte) r1;
    putShort(bytesRef.buffer, 2, (short) len);
    return bytesRef;
  }

  public static BytesRef readMessageType2(InputStream input, int b1, BytesRef bytesRef) throws IOException {
    int r1 = input.read();
    int r2 = input.read();
    int len = readUnsignedShort(input);
    if (!isValidLength(len)) {
      return null;
    }

    bytesRef.growSetLength(len + 5);
    readFully(input, bytesRef.buffer, 5, len);
    bytesRef.buffer[0] = (byte) b1;
    bytesRef.buffer[1] = (byte) r1;
    bytesRef.buffer[2] = (byte) r2;
    putShort(bytesRef.buffer, 3, (short) len);
    return bytesRef;
  }

  public static BytesRef readMessageType3(InputStream input, int b1, BytesRef bytesRef) throws IOException {
    int len = input.read();
    if (!isValidLength(len)) {
      return null;
    }
    bytesRef.growSetLength(len);
    readFully(input, bytesRef.buffer, 2, len - 2);
    bytesRef.buffer[0] = (byte) b1;
    bytesRef.buffer[1] = (byte) len;
    return bytesRef;
  }

  public static int readUnsignedShort(InputStream input) throws IOException {
    int ch1 = input.read();
    int ch2 = input.read();
    if ((ch1 | ch2) < 0)
      throw new EOFException();
    return (ch1 << 8) + (ch2 << 0);
  }

  public static short getShort(byte[] b, int off) {
    return (short) ((b[off + 1] & 0xFF) + (b[off] << 8));
  }

  public static void putShort(byte[] b, int off, short val) {
    b[off + 1] = (byte) (val);
    b[off] = (byte) (val >>> 8);
  }

  public static boolean isValidLength(int len) {
    return len >= 0;
  }

  public static void readFully(InputStream input, byte[] buf, int off, int len) throws IOException {
    if (len < 0) {
      throw new IndexOutOfBoundsException();
    }
    int n = 0;
    while (n < len) {
      int count = input.read(buf, off + n, len - n);
      if (count < 0) {
        throw new EOFException();
      }
      n += count;
    }
  }

  public static String bytesToHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      builder.append((char) HES_ARRAY[v >>> 4]);
      builder.append((char) HES_ARRAY[v & 0x0F]);
      builder.append(' ');
    }
    return builder.toString();
  }
}
