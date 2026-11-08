# Opts a Windows executable into the segment heap by adding
#   <heapType xmlns="http://schemas.microsoft.com/SMI/2020/WindowsSettings">SegmentHeap</heapType>
# to the windowsSettings of its embedded manifest (RT_MANIFEST, id 1).
#
# Why: every native allocation in the app (the JVM's own, Skia, FFmpeg, Direct3D, the CRT behind all of them)
# goes through the process heap. The default NT heap keeps freed blocks committed in up-to-16 MB segments and
# fragments under the churn of a UI that decodes covers and audio all day; the segment heap is the allocator
# Windows itself and Edge moved to for exactly that reason (Windows 10 2004+; older builds ignore the element).
#
# Uses the Win32 resource-update API directly so the build needs no Windows SDK (mt.exe).
param([Parameter(Mandatory = $true)][string[]]$Executable)
$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class KtManifest {
  [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)] static extern IntPtr LoadLibraryEx(string f, IntPtr h, uint flags);
  [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr FindResource(IntPtr h, IntPtr name, IntPtr type);
  [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr LoadResource(IntPtr h, IntPtr r);
  [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr LockResource(IntPtr r);
  [DllImport("kernel32.dll", SetLastError=true)] static extern uint SizeofResource(IntPtr h, IntPtr r);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool FreeLibrary(IntPtr h);
  [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)] static extern IntPtr BeginUpdateResource(string f, bool del);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool UpdateResource(IntPtr h, IntPtr type, IntPtr name, ushort lang, byte[] data, uint size);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool EndUpdateResource(IntPtr h, bool discard);
  public static string Read(string exe) {
    IntPtr m = LoadLibraryEx(exe, IntPtr.Zero, 0x2);
    if (m == IntPtr.Zero) return null;
    try {
      IntPtr r = FindResource(m, (IntPtr)1, (IntPtr)24);
      if (r == IntPtr.Zero) return null;
      uint n = SizeofResource(m, r);
      byte[] b = new byte[n];
      Marshal.Copy(LockResource(LoadResource(m, r)), b, 0, (int)n);
      return System.Text.Encoding.UTF8.GetString(b);
    } finally { FreeLibrary(m); }
  }
  public static void Write(string exe, string xml) {
    byte[] b = System.Text.Encoding.UTF8.GetBytes(xml);
    IntPtr h = BeginUpdateResource(exe, false);
    if (h == IntPtr.Zero) throw new Exception("BeginUpdateResource failed: " + Marshal.GetLastWin32Error());
    if (!UpdateResource(h, (IntPtr)24, (IntPtr)1, 0, b, (uint)b.Length)) { EndUpdateResource(h, true); throw new Exception("UpdateResource failed: " + Marshal.GetLastWin32Error()); }
    if (!EndUpdateResource(h, false)) throw new Exception("EndUpdateResource failed: " + Marshal.GetLastWin32Error());
  }
}
"@

$heapType = '<heapType xmlns="http://schemas.microsoft.com/SMI/2020/WindowsSettings">SegmentHeap</heapType>'
foreach ($exe in $Executable) {
    if (-not (Test-Path -LiteralPath $exe)) { continue }
    $path = (Resolve-Path -LiteralPath $exe).Path
    $xml = [KtManifest]::Read($path)
    if (-not $xml) { Write-Warning "No embedded manifest in $path; left as is."; continue }
    $xml = $xml.TrimStart([char]0xFEFF)
    if ($xml -match 'SegmentHeap') { Write-Host "Segment heap already enabled: $path"; continue }
    if ($xml -match '<(\w+:)?windowsSettings[^>]*>') {
        # Into the existing block: a second <application> element would make the manifest invalid.
        $xml = $xml -replace '(<(\w+:)?windowsSettings[^>]*>)', "`$1$heapType"
    } else {
        $xml = $xml -replace '</assembly>', "<application xmlns=`"urn:schemas-microsoft-com:asm.v3`"><windowsSettings>$heapType</windowsSettings></application></assembly>"
    }
    # jpackage writes the launcher read-only, which BeginUpdateResource refuses (ERROR_WRITE_PROTECT).
    $item = Get-Item -LiteralPath $path
    $wasReadOnly = $item.IsReadOnly
    if ($wasReadOnly) { $item.IsReadOnly = $false }
    try {
        [KtManifest]::Write($path, $xml)
    } finally {
        if ($wasReadOnly) { (Get-Item -LiteralPath $path).IsReadOnly = $true }
    }
    Write-Host "Segment heap enabled: $path"
}
