package rdp.proxy.spi;

public class RdpSetting {

  private static final String INT_TYPE = "i";
  private static final String STRING_TYPE = "s";
  private final Long _longValue;
  private final String _stringValue;
  private final String _name;

  private RdpSetting(String name, String value) {
    _name = name;
    _longValue = null;
    _stringValue = value;
  }

  private RdpSetting(String name, Long value) {
    _name = name;
    _longValue = value;
    _stringValue = null;
  }

  public Long getLongValue() {
    return _longValue;
  }

  public String getStringValue() {
    return _stringValue;
  }

  public String getName() {
    return _name;
  }

  public static RdpSetting create(String name, Long value) {
    return new RdpSetting(name, value);
  }

  public static RdpSetting create(String name, String value) {
    return new RdpSetting(name, value);
  }

  @Override
  public String toString() {
    return _name + ":" + (_longValue == null ? STRING_TYPE + ":" + _stringValue : INT_TYPE + ":" + _longValue);
  }

  public static RdpSetting parse(String s) {
    int endOfName = s.indexOf(':');
    String name = s.substring(0, endOfName);
    int endOfType = s.indexOf(':', endOfName + 1);
    String type = s.substring(endOfName + 1, endOfType);
    String value = s.substring(endOfType + 1);
    switch (type) {
    case STRING_TYPE:
      return RdpSetting.create(name, value);
    case INT_TYPE:
      return RdpSetting.create(name, Long.parseLong(value));
    default:
      throw new RuntimeException("Type [" + type + "] not found");
    }
  }

}
