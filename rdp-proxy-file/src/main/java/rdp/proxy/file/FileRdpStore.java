package rdp.proxy.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import rdp.proxy.spi.BaseRdpStore;
import rdp.proxy.spi.RdpSetting;
import rdp.proxy.spi.config.ConfigUtil;

public class FileRdpStore extends BaseRdpStore {

  private final File _dir;

  public FileRdpStore() {
    _dir = ConfigUtil.loadProperty("RDP_INFO_FILE", value -> new File(value));
  }

  @Override
  public boolean isValidUser(String user) throws IOException {
    return getUserFile(user).exists();
  }

  @Override
  public List<RdpSetting> getRdpSettings(List<RdpSetting> defaultSettings, String user, String id, String rdpHostname,
      int rdpPort) throws IOException {
    Map<String, RdpSetting> map = toMap(defaultSettings);
    try (InputStream inputStream = new FileInputStream(new File(_dir, user))) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trim = line.trim();
          if (!trim.isEmpty()) {
            RdpSetting rdpSetting = RdpSetting.parse(trim);
            map.put(rdpSetting.getName(), rdpSetting);
          }
        }
      }
      return ImmutableList.copyOf(map.values());
    }
  }

  private File getUserFile(String user) {
    return new File(_dir, user);
  }

  private Map<String, RdpSetting> toMap(List<RdpSetting> rdpSettings) {
    Map<String, RdpSetting> map = new HashMap<>();
    for (RdpSetting rdpSetting : rdpSettings) {
      map.put(rdpSetting.getName(), rdpSetting);
    }
    return map;
  }
}
