package main

import (
	"os"
	"os/exec"
	"path/filepath"
)

func main() {
	executablePath, err := os.Executable()
	if err != nil {
		os.Exit(1)
	}
	home, err := filepath.Abs(filepath.Dir(executablePath))
	if err != nil {
		os.Exit(1)
	}
	jar := filepath.Join(home, "openadt.jar")
	java, err := exec.LookPath("java")
	if err != nil {
		os.Stderr.WriteString("java not found on PATH; install a JDK (for example Temurin 21)\n")
		os.Exit(1)
	}
	args := append([]string{"-jar", jar}, os.Args[1:]...)
	cmd := exec.Command(java, args...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		if exit, ok := err.(*exec.ExitError); ok && exit.ExitCode() >= 0 {
			os.Exit(exit.ExitCode())
		}
		os.Exit(1)
	}
}
