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
            return RunPowerShellLauncher(home, launcher, args);
        }

        return RunJavaJar(home, args);
    }

    private static int RunPowerShellLauncher(string home, string launcher, string[] args)
    {
        var pwsh = ResolvePwshExecutable();
        if (pwsh is not null)
        {
            return RunPowerShellExecutable(pwsh, home, launcher, args);
        }

        var systemRoot = Environment.GetFolderPath(Environment.SpecialFolder.Windows);
        if (string.IsNullOrWhiteSpace(systemRoot))
        {
            systemRoot = Environment.GetEnvironmentVariable("SystemRoot") ?? @"C:\Windows";
        }
        var systemRootFull = Path.GetFullPath(systemRoot);
        var powershell = Path.Combine(systemRootFull, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        var start = new ProcessStartInfo(powershell)
        {
            UseShellExecute = false,
        };
        start.ArgumentList.Add("-NoProfile");
        start.ArgumentList.Add("-ExecutionPolicy");
        start.ArgumentList.Add("Bypass");
        start.ArgumentList.Add("-File");
        start.ArgumentList.Add(launcher);
        start.Environment["OPENADT_HOME"] = home;
        start.Environment["OPENADT_ARG_COUNT"] = args.Length.ToString();
        for (var i = 0; i < args.Length; i++)
        {
            start.Environment[$"OPENADT_ARG_{i}"] = args[i];
        }
        try
        {
            using var process = Process.Start(start) ?? throw new InvalidOperationException("Failed to start PowerShell launcher.");
            process.WaitForExit();
            return process.ExitCode;
        }
        catch (Win32Exception)
        {
            var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            if (!string.IsNullOrWhiteSpace(programFiles) && Path.IsPathRooted(programFiles))
            {
                var pwshExe = Path.Join(
                    programFiles,
                    "PowerShell",
                    "7",
                    "pwsh.exe");
                if (File.Exists(pwshExe))
                {
                    return RunPowerShellExecutable(pwshExe, home, launcher, args);
                }
            }
            Console.Error.WriteLine("Failed to start PowerShell for openadt launcher.");
            return 1;
        }
    }

    private static string? ResolvePwshExecutable()
    {
        var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        if (!string.IsNullOrWhiteSpace(programFiles) && Path.IsPathRooted(programFiles))
        {
            var pwshExe = Path.Join(programFiles, "PowerShell", "7", "pwsh.exe");
            if (File.Exists(pwshExe))
            {
                return pwshExe;
            }
        }

        var fromPath = Environment.GetEnvironmentVariable("PATH");
        if (!string.IsNullOrWhiteSpace(fromPath))
        {
            foreach (var segment in fromPath.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries))
            {
                var candidate = Path.Combine(segment.Trim(), "pwsh.exe");
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static int RunPowerShellExecutable(string powershell, string home, string launcher, string[] args)
    {
        var start = new ProcessStartInfo(powershell)
        {
            UseShellExecute = false,
        };
        start.ArgumentList.Add("-NoProfile");
        start.ArgumentList.Add("-ExecutionPolicy");
        start.ArgumentList.Add("Bypass");
        start.ArgumentList.Add("-File");
        start.ArgumentList.Add(launcher);
        start.Environment["OPENADT_HOME"] = home;
        start.Environment["OPENADT_ARG_COUNT"] = args.Length.ToString();
        for (var i = 0; i < args.Length; i++)
        {
            start.Environment[$"OPENADT_ARG_{i}"] = args[i];
        }
        using var process = Process.Start(start) ?? throw new InvalidOperationException("Failed to start PowerShell launcher.");
        process.WaitForExit();
        return process.ExitCode;
    }

    private static int RunJavaJar(string home, string[] args)
    {
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
