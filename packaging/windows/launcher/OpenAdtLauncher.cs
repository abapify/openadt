using System;
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
        using var process = Process.Start(start);
        if (process == null)
        {
            Console.Error.WriteLine("java not found on PATH; install a JDK (for example Temurin 21)");
            return 1;
        }
        process.WaitForExit();
        return process.ExitCode;
    }
}
