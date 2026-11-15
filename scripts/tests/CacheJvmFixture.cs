using System;
using System.IO;
using System.Linq;
using System.Diagnostics;
using System.Threading;
public class CacheJvmFixture {
  public static int Main(string[] args) {
    if (args.Length == 1 && args[0].StartsWith("@")) {
      string argumentFile = args[0].Substring(1);
      if (argumentFile.Any(c => c > 127)) {
        Console.Error.WriteLine("Expected an ASCII argument file path for the Windows Java launcher");
        return 10;
      }
      args = File.ReadAllLines(argumentFile).Select(line =>
        System.Text.RegularExpressions.Regex.Unescape(line.Substring(1, line.Length - 2))).ToArray();
    }
    string argumentLog = Environment.GetEnvironmentVariable("FAKE_ARGUMENT_LOG");
    if (argumentLog != null) File.WriteAllLines(argumentLog, args);
    Func<string,string> option = prefix => args.FirstOrDefault(a => a.StartsWith(prefix)) == null ? null : args.First(a => a.StartsWith(prefix)).Substring(prefix.Length);
    string mode = option("-XX:AOTMode=");
    if (mode == "record") {
      Console.WriteLine("fixture running"); Console.Out.Flush();
      if (Environment.GetEnvironmentVariable("FAKE_DELAY") == "1") Thread.Sleep(5000);
      string config = option("-XX:AOTConfiguration=");
      File.WriteAllText(config, "recorded");
      Console.WriteLine("KittyTune startup cache training finished"); Console.Out.Flush();
    } else if (mode == "create") {
      if(Environment.GetEnvironmentVariable("FAKE_FAIL") == "1") { Console.Error.WriteLine("assembly fixture failure"); return 7; }
      File.WriteAllText(option("-XX:AOTCache="), "fresh-cache");
    } else if (mode == "on" && !File.Exists(option("-XX:AOTCache="))) return 8;
    if (mode == "on") {
      if (Environment.GetEnvironmentVariable("FAKE_HOLD") == "1") {
        string path = Path.Combine(Path.GetDirectoryName(option("-XX:AOTCache=")), "locked.tmp");
        File.WriteAllText(path, "locked");
        string ready = Environment.GetEnvironmentVariable("FAKE_LOCK_READY");
        File.WriteAllText(ready, path);
        for(int i=0;i<500 && !File.Exists(ready + ".locked");i++) Thread.Sleep(10);
        if(!File.Exists(ready + ".locked")) return 9;
      }
    }
    return 0;
  }
}
