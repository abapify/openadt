using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;

internal static class Program
{
    private static int Main(string[] args)
    {
        var home = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        var jar = Path.Combine(home, "openadt.jar");
        var start = new ProcessStartInfo("java")
        {
            UseShellExecute = false,
        };
        start.ArgumentList.Add("-jar");
        start.ArgumentList.Add(jar);
        foreach (var arg in args)
        {
            start.ArgumentList.Add(arg);
        }
        Process process;
        try
        {
            process = Process.Start(start) ?? throw new InvalidOperationException("Failed to start java process.");
        }
        catch (Win32Exception)
        {
            Console.Error.WriteLine("java not found on PATH; install a JDK (for example Temurin 21)");
            return 1;
        }
        catch (InvalidOperationException)
        {
            Console.Error.WriteLine("Failed to start java process.");
            return 1;
        }
        using (process)
        {
            process.WaitForExit();
            return process.ExitCode;
        }
    }
}
