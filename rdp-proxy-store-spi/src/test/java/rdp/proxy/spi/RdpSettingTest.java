package rdp.proxy.spi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RdpSettingTest {

  @Test
  public void test1() {
    testSettingParser("test:i:", "test", null, null, false);
  }

  @Test
  public void test2() {
    testSettingParser("test:s:", "test", "", null, false);
  }

  @Test
  public void test3() {
    testSettingParser("test:i", "test", null, null, true);
  }

  @Test
  public void test4() {
    testSettingParser("test:s", "test", null, null, true);
  }

  @Test
  public void test5() {
    testSettingParser("test:i:12345", "test", null, 12345L, false);
  }

  @Test
  public void test6() {
    testSettingParser("test:s:12345", "test", "12345", null, false);
  }

  private void testSettingParser(String str, String name, String stringValue, Long intValue, boolean deleted) {
    RdpSetting rdpSetting = RdpSetting.parse(str);
    assertEquals(str, rdpSetting.toString());
    assertEquals(name, rdpSetting.getName());
    assertEquals(stringValue, rdpSetting.getStringValue());
    assertEquals(intValue, rdpSetting.getLongValue());
    assertEquals(deleted, rdpSetting.isDeleted());
  }

}
