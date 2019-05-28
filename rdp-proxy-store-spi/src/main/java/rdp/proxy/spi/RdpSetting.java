package rdp.proxy.spi;

public class RdpSetting {

  private static final String INT_TYPE = "i";
  private static final String STRING_TYPE = "s";
  private final Long _longValue;
  private final String _stringValue;
  private final String _name;
  private final boolean _stringType;
  private final boolean _intType;
  private final boolean _deleted;

  private RdpSetting(String name, String value) {
    this(name, value, false);
  }

  private RdpSetting(String name, Long value) {
    this(name, value, false);
  }

  private RdpSetting(String name, String value, boolean deleted) {
    _name = name;
    _longValue = null;
    _stringValue = value;
    _stringType = true;
    _intType = false;
    _deleted = deleted;
  }

  private RdpSetting(String name, Long value, boolean deleted) {
    _name = name;
    _longValue = value;
    _stringValue = null;
    _stringType = false;
    _intType = true;
    _deleted = deleted;
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

  public boolean isDeleted() {
    return _deleted;
  }

  public boolean isStringType() {
    return _stringType;
  }

  public boolean isIntType() {
    return _intType;
  }

  public static RdpSetting create(String name, Long value) {
    return new RdpSetting(name, value);
  }

  public static RdpSetting create(String name, String value) {
    return new RdpSetting(name, value);
  }

  public static RdpSetting deleteString(String name) {
    return new RdpSetting(name, (String) null, true);
  }

  public static RdpSetting deleteInt(String name) {
    return new RdpSetting(name, (Long) null, true);
  }

  @Override
  public String toString() {
    if (isDeleted()) {
      return _name + ":" + (_stringType ? STRING_TYPE : INT_TYPE);
    }
    return _name + ":"
        + (_stringType ? STRING_TYPE + ":" + _stringValue : INT_TYPE + ":" + (_longValue == null ? "" : _longValue));
  }

  public static RdpSetting parse(String s) {
    int endOfName = s.indexOf(':');
    String name = s.substring(0, endOfName);
    int endOfType = s.indexOf(':', endOfName + 1);
    if (endOfType < 0) {
      return RdpSetting.createDelete(name, s.substring(endOfName + 1));
    }
    String type = s.substring(endOfName + 1, endOfType);
    String value = s.substring(endOfType + 1);
    switch (type) {
    case STRING_TYPE:
      return RdpSetting.create(name, value);
    case INT_TYPE:
      if (value == null || value.isEmpty()) {
        return RdpSetting.create(name, (Long) null);
      }
      return RdpSetting.create(name, Long.parseLong(value));
    default:
      throw new RuntimeException("Type [" + type + "] not found");
    }
  }

  public static RdpSetting createDelete(String name, String type) {
    switch (type) {
    case STRING_TYPE:
      return deleteString(name);
    case INT_TYPE:
      return deleteInt(name);
    default:
      throw new RuntimeException("Type [" + type + "] not found");
    }
  }

}
