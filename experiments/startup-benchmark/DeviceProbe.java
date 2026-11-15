import java.lang.reflect.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
public class DeviceProbe {
  public interface ProcessHandles extends StdCallLibrary {
    boolean GetProcessHandleCount(HANDLE process, IntByReference count);
  }
  private static int handles() {
    IntByReference value = new IntByReference();
    ProcessHandles api = Native.load("kernel32", ProcessHandles.class);
    if (!api.GetProcessHandleCount(Kernel32.INSTANCE.GetCurrentProcess(), value))
      throw new IllegalStateException("GetProcessHandleCount failed");
    return value.getValue();
  }
  public static void main(String[] args) throws Exception {
    if (args.length > 0 && (args[0].equals("names") || args[0].equals("handles"))) {
      Class<?> type = Class.forName("com.alananasss.kittytune.data.audio.WindowsDeviceNames");
      Object instance = type.getField("INSTANCE").get(null);
      if (args[0].equals("handles")) {
        Method read = type.getMethod("audioEndpoints");
        for (int i = 0; i < 20; i++) read.invoke(instance);
        int before = handles();
        for (int i = 0; i < 200; i++) read.invoke(instance);
        int after = handles();
        System.out.printf("handles_before=%d,handles_after=%d,reads=200%n", before, after);
        if (after > before + 2) throw new IllegalStateException("Device handles accumulated");
        return;
      }
      for (String name : (List<String>)type.getMethod("audioEndpoints").invoke(instance))
        System.out.println("name=" + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)));
      return;
    }
    Class<?> type = Class.forName("com.alananasss.kittytune.data.audio.HeadphoneDetector");
    Object instance = type.getField("INSTANCE").get(null);
    Method probe = Arrays.stream(type.getDeclaredMethods())
      .filter(m -> m.getName().startsWith("windowsOutput") && (m.getParameterCount() == 0 || Modifier.isStatic(m.getModifiers()) && m.getName().endsWith("$default")))
      .findFirst().orElseThrow();
    probe.setAccessible(true);
    long start = System.nanoTime();
    Object result = probe.getParameterCount() == 0 ? probe.invoke(instance) : probe.invoke(null, instance, null, null, 3, null);
    System.out.printf(Locale.ROOT, "probe_ms=%.3f,detected=%s%n", (System.nanoTime() - start) / 1e6, result != null);
  }
}
