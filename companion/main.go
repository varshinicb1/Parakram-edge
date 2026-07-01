package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"syscall"
	"time"
)

var version = "1.0.0"

func main() {
	pairMode := flag.Bool("pair", false, "Pair with a Parakram device")
	discoverMode := flag.Bool("discover", false, "Discover Parakram devices on the LAN")
	uploadFlag := flag.String("upload", "", "Upload a file to the paired device")
	downloadFlag := flag.String("download", "", "Download a file from the paired device")
	listFlag := flag.Bool("list", false, "List files on the paired device")
	showVersion := flag.Bool("version", false, "Show version")
	flag.Parse()

	if *showVersion {
		fmt.Printf("Parakram Edge Companion v%s (%s/%s)\n", version, runtime.GOOS, runtime.GOARCH)
		return
	}

	cfg, err := loadConfig()
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	if *discoverMode {
		discoverAndPrint()
		return
	}

	if *pairMode {
		pairInteractive(cfg)
		return
	}

	if *uploadFlag != "" {
		requirePaired(cfg)
		if err := uploadInteractive(cfg.ServerURL, cfg.APIKey, *uploadFlag); err != nil {
			log.Fatalf("Upload failed: %v", err)
		}
		fmt.Println("Upload complete.")
		return
	}

	if *downloadFlag != "" {
		requirePaired(cfg)
		if err := downloadInteractive(cfg.ServerURL, cfg.APIKey, *downloadFlag, "."); err != nil {
			log.Fatalf("Download failed: %v", err)
		}
		fmt.Println("Download complete.")
		return
	}

	if *listFlag {
		requirePaired(cfg)
		files, err := listRemoteFiles(cfg.ServerURL, cfg.APIKey)
		if err != nil {
			log.Fatalf("List files failed: %v", err)
		}
		fmt.Println("Remote files:")
		for _, f := range files {
			fmt.Println("  ", f)
		}
		return
	}

	startTray(cfg)
}

func requirePaired(cfg *Config) {
	if cfg.ServerURL == "" || cfg.APIKey == "" {
		log.Fatal("Not paired. Run with --pair first.")
	}
}

func discoverAndPrint() {
	fmt.Println("Scanning for Parakram devices on the LAN...")
	devices, err := discoverDevices(5 * time.Second)
	if err != nil {
		log.Fatalf("Discovery failed: %v", err)
	}
	if len(devices) == 0 {
		fmt.Println("No devices found. Ensure Parakram is running on your phone and connected to the same network.")
		return
	}
	for _, d := range devices {
		name := d.Name
		if name == "" {
			name = "Unnamed"
		}
		fmt.Printf("  %s → http://%s:%d\n", name, d.IP, d.Port)
	}
}

func pairInteractive(cfg *Config) {
	fmt.Print("Server URL (e.g., http://192.168.1.100:8080): ")
	var serverURL, handshakeID, challenge, pin string
	fmt.Scanln(&serverURL)
	serverURL = strings.TrimRight(serverURL, "/")

	fmt.Print("Handshake ID (from QR): ")
	fmt.Scanln(&handshakeID)
	fmt.Print("Challenge (from QR): ")
	fmt.Scanln(&challenge)
	fmt.Print("PIN (from phone screen): ")
	fmt.Scanln(&pin)

	hostname, _ := os.Hostname()
	mac := getMAC()

	fmt.Printf("Pairing with %s...\n", serverURL)
	resp, err := pairWithDevice(serverURL, handshakeID, hostname, mac, challenge, pin)
	if err != nil {
		log.Fatalf("Pairing failed: %v", err)
	}

	fmt.Printf("✅ Paired successfully with %s\n", resp.DeviceName)
	fmt.Printf("   API Key: %s\n", resp.APIKey)

	cfg.ServerURL = serverURL
	cfg.APIKey = resp.APIKey
	cfg.DeviceName = resp.DeviceName
	if err := saveConfig(cfg); err != nil {
		log.Printf("Warning: failed to save config: %v", err)
	}
}

var (
	sigChan = make(chan os.Signal, 1)
)

func waitForExit() {
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	<-sigChan
}
