using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;

internal static class Program
{
    private static int Main(string[] args)
    {
        var home = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        var launcher = Path.Combine(home, "bin", "openadt-launcher.ps1");
        if (File.Exists(launcher))
        {
            var start = new ProcessStartInfo("powershell.exe")
            {
                UseShellExecute = false,
            };
            start.ArgumentList.Add("-NoProfile");
            start.ArgumentList.Add("-ExecutionPolicy");
            start.ArgumentList.Add("Bypass");
            start.ArgumentList.Add("-File");
            start.ArgumentList.Add(launcher);
            foreach (var arg in args)
            {
                start.ArgumentList.Add(arg);
            }
            start.Environment["OPENADT_HOME"] = home;
            try
            {
                using var process = Process.Start(start) ?? throw new InvalidOperationException("Failed to start PowerShell launcher.");
                process.WaitForExit();
                return process.ExitCode;
            }
            catch (Win32Exception)
            {
                Console.Error.WriteLine("Failed to start PowerShell for openadt launcher.");
                return 1;
            }
        }

        var jar = Path.Combine(home, "openadt.jar");
        var javaStart = new ProcessStartInfo("java")
        {
            UseShellExecute = false,
        };
        javaStart.ArgumentList.Add("-jar");
        javaStart.ArgumentList.Add(jar);
        foreach (var arg in args)
        {
            javaStart.ArgumentList.Add(arg);
        }
        try
        {
            using var process = Process.Start(javaStart) ?? throw new InvalidOperationException("Failed to start java process.");
            process.WaitForExit();
            return process.ExitCode;
        }
        catch (Win32Exception)
        {
            Console.Error.WriteLine("java not found on PATH; install a JDK (for example Temurin 21)");
            return 1;
        }
    }
}
