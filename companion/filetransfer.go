package main

import (
	"fmt"
	"os"
	"path/filepath"
)

func readFile(path string) ([]byte, error) {
	return os.ReadFile(path)
}

func writeFile(path string, data []byte) error {
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return err
	}
	return os.WriteFile(path, data, 0644)
}

func uploadInteractive(serverURL, apiKey, localPath string) error {
	info, err := os.Stat(localPath)
	if err != nil {
		return fmt.Errorf("stat file: %w", err)
	}
	if info.IsDir() {
		return fmt.Errorf("cannot upload a directory: %s", localPath)
	}
	name := filepath.Base(localPath)
	if err := uploadFile(serverURL, apiKey, localPath, name); err != nil {
		return err
	}
	return nil
}

func downloadInteractive(serverURL, apiKey, remoteName, destDir string) error {
	data, err := downloadFile(serverURL, apiKey, remoteName)
	if err != nil {
		return err
	}
	dest := filepath.Join(destDir, remoteName)
	return writeFile(dest, data)
}

func listRemoteFiles(serverURL, apiKey string) ([]string, error) {
	return listFiles(serverURL, apiKey)
}
