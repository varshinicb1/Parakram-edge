package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type SecurePairingRequest struct {
	HandshakeID  string `json:"handshakeId"`
	ClientName   string `json:"clientName"`
	ClientMAC    string `json:"clientMac"`
	ResponseHash string `json:"responseHash"`
}

type SecurePairingResponse struct {
	Success    bool   `json:"success"`
	Message    string `json:"message"`
	APIKey     string `json:"apiKey,omitempty"`
	DeviceName string `json:"deviceName,omitempty"`
}

type StatusResponse struct {
	Status string `json:"status"`
	Device string `json:"device"`
}

func pairWithDevice(serverURL, handshakeID, clientName, clientMAC, challenge, pin string) (*SecurePairingResponse, error) {
	input := challenge + pin
	hash := fmt.Sprintf("%x", sha256.Sum256([]byte(input)))

	req := SecurePairingRequest{
		HandshakeID:  handshakeID,
		ClientName:   clientName,
		ClientMAC:    clientMAC,
		ResponseHash: hash,
	}
	body, _ := json.Marshal(req)

	resp, err := http.Post(serverURL+"/api/auth/pair/secure-handshake", "application/json", bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("pair request failed: %w", err)
	}
	defer resp.Body.Close()

	var result SecurePairingResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("decode response: %w", err)
	}
	if !result.Success {
		return &result, fmt.Errorf("pairing rejected: %s", result.Message)
	}
	return &result, nil
}

func getStatus(serverURL, apiKey string) (*StatusResponse, error) {
	req, _ := http.NewRequest("GET", serverURL+"/api/status", nil)
	req.Header.Set("X-Agent-Key", apiKey)
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("status request failed: %w", err)
	}
	defer resp.Body.Close()

	var s StatusResponse
	if err := json.NewDecoder(resp.Body).Decode(&s); err != nil {
		return nil, fmt.Errorf("decode status: %w", err)
	}
	return &s, nil
}

func pushClipboard(serverURL, apiKey, text string) error {
	payload := map[string]string{"text": text}
	body, _ := json.Marshal(payload)

	req, _ := http.NewRequest("POST", serverURL+"/api/clipboard/push", bytes.NewReader(body))
	req.Header.Set("X-Agent-Key", apiKey)
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("clipboard push failed: %w", err)
	}
	defer resp.Body.Close()
	return nil
}

func pullClipboard(serverURL, apiKey string) (string, error) {
	req, _ := http.NewRequest("GET", serverURL+"/api/clipboard/pull", nil)
	req.Header.Set("X-Agent-Key", apiKey)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("clipboard pull failed: %w", err)
	}
	defer resp.Body.Close()

	var result map[string]string
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", fmt.Errorf("decode clipboard: %w", err)
	}
	return result["text"], nil
}

func uploadFile(serverURL, apiKey, localPath, remoteName string) error {
	data, err := readFile(localPath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	req, _ := http.NewRequest("PUT", serverURL+"/api/files/upload/"+remoteName, bytes.NewReader(data))
	req.Header.Set("X-Agent-Key", apiKey)
	req.Header.Set("Content-Type", "application/octet-stream")

	client := &http.Client{Timeout: 120 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("upload failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("upload rejected (%d): %s", resp.StatusCode, string(body))
	}
	return nil
}

func downloadFile(serverURL, apiKey, remoteName string) ([]byte, error) {
	req, _ := http.NewRequest("GET", serverURL+"/api/files/download/"+remoteName, nil)
	req.Header.Set("X-Agent-Key", apiKey)

	client := &http.Client{Timeout: 120 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("download failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("download rejected (%d): %s", resp.StatusCode, string(body))
	}
	return io.ReadAll(resp.Body)
}

func listFiles(serverURL, apiKey string) ([]string, error) {
	req, _ := http.NewRequest("GET", serverURL+"/api/files/list", nil)
	req.Header.Set("X-Agent-Key", apiKey)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("list files failed: %w", err)
	}
	defer resp.Body.Close()

	var result map[string][]string
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("decode file list: %w", err)
	}
	return result["files"], nil
}
