package main

import (
	"fmt"
	"net"
	"os"
	"time"

	"github.com/getlantern/systray"
)

var (
	cfg               *Config
	serverURL         string
	apiKey            string
	deviceName        string
	statusConnected   bool
	menuStatus        *systray.MenuItem
	menuClipboard     *systray.MenuItem
	menuUpload        *systray.MenuItem
	menuDownload      *systray.MenuItem
	menuPair          *systray.MenuItem
	menuAutoStart     *systray.MenuItem
	menuQuit          *systray.MenuItem
)

func startTray(c *Config) {
	cfg = c
	serverURL = c.ServerURL
	apiKey = c.APIKey
	deviceName = c.DeviceName
	clipboardEnabled = c.APIKey != ""

	systray.Run(onReady, onExit)
}

func onReady() {
	systray.SetTitle("Parakram")
	systray.SetTooltip("Parakram Edge Companion")

	// Load generated icon
	systray.SetIcon(getIcon())

	menuStatus = systray.AddMenuItem("Disconnected", "Connection status")
	menuStatus.Disable()

	// Add connection status submenu
	menuReconnect := systray.AddMenuItem("Reconnect / Re-pair", "Pair with a device")
	systray.AddSeparator()

	menuClipboard = systray.AddMenuItem("Clipboard Sync: ON", "Toggle clipboard sync")
	if !clipboardEnabled {
		menuClipboard.SetTitle("Clipboard Sync: OFF (not paired)")
		menuClipboard.Disable()
	}
	systray.AddSeparator()

	menuUpload = systray.AddMenuItem("Upload File...", "Upload a file to the phone")
	menuDownload = systray.AddMenuItem("Download File...", "Download a file from the phone")
	systray.AddSeparator()

	menuPair = systray.AddMenuItem("Pair with Device", "Pair with a Parakram device")
	menuAutoStart = systray.AddMenuItem("Auto-start on login", "Auto-start on login")
	menuQuit = systray.AddMenuItem("Quit", "Exit the companion")

	// Update status if paired
	if serverURL != "" && apiKey != "" {
		go checkConnection()
		updateStatusUI()
	}

	// Start clipboard sync if enabled
	if clipboardEnabled {
		if err := initClipboard(); err == nil {
			startClipboardSync(serverURL, apiKey, func(msg string) {
				notify(msg)
			})
		}
	}

	// Menu handlers
	go func() {
		for {
			select {
			case <-menuPair.ClickedCh:
				pairViaDialog()
			case <-menuClipboard.ClickedCh:
				toggleClipboard()
			case <-menuUpload.ClickedCh:
				uploadDialog()
			case <-menuDownload.ClickedCh:
				downloadDialog()
			case <-menuAutoStart.ClickedCh:
				toggleAutoStart()
			case <-menuReconnect.ClickedCh:
				go checkConnection()
			case <-menuQuit.ClickedCh:
				systray.Quit()
				return
			}
		}
	}()
}

func onExit() {
	os.Exit(0)
}

func updateStatusUI() {
	if statusConnected {
		menuStatus.SetTitle(fmt.Sprintf("Connected to %s", deviceName))
	} else {
		menuStatus.SetTitle("Disconnected")
	}
}

func checkConnection() {
	status, err := getStatus(serverURL, apiKey)
	if err != nil {
		statusConnected = false
	} else {
		statusConnected = status.Status == "online"
		deviceName = status.Device
		cfg.DeviceName = deviceName
		saveConfig(cfg)
	}
	updateStatusUI()

	// Periodic check
	for range time.Tick(30 * time.Second) {
		status, err := getStatus(serverURL, apiKey)
		if err != nil {
			statusConnected = false
		} else {
			statusConnected = status.Status == "online"
		}
		updateStatusUI()
	}
}

func pairViaDialog() {
	fmt.Print("Server URL (e.g., http://192.168.1.100:8080): ")
	var url, handshakeID, challenge, pin string
	fmt.Scanln(&url)
	url = fmt.Sprintf("http://%s", url)
	fmt.Print("Handshake ID: ")
	fmt.Scanln(&handshakeID)
	fmt.Print("Challenge: ")
	fmt.Scanln(&challenge)
	fmt.Print("PIN: ")
	fmt.Scanln(&pin)

	hostname, _ := os.Hostname()
	mac := getMAC()

	resp, err := pairWithDevice(url, handshakeID, hostname, mac, challenge, pin)
	if err != nil {
		notify("Pairing failed: " + err.Error())
		return
	}

	cfg.ServerURL = url
	cfg.APIKey = resp.APIKey
	cfg.DeviceName = resp.DeviceName
	saveConfig(cfg)

	serverURL = url
	apiKey = resp.APIKey
	deviceName = resp.DeviceName
	statusConnected = true
	clipboardEnabled = true

	menuClipboard.SetTitle("Clipboard Sync: ON")
	menuClipboard.Enable()
	updateStatusUI()

	notify(fmt.Sprintf("Paired with %s", resp.DeviceName))
}

func toggleClipboard() {
	if !clipboardEnabled {
		return
	}

	if clipboardEnabled {
		clipboardEnabled = false
		menuClipboard.SetTitle("Clipboard Sync: OFF")
		notify("Clipboard sync paused")
	} else {
		clipboardEnabled = true
		menuClipboard.SetTitle("Clipboard Sync: ON")
		notify("Clipboard sync resumed")
	}
}

func uploadDialog() {
	if !statusConnected {
		notify("Not connected. Pair first.")
		return
	}
	var path string
	fmt.Print("File path to upload: ")
	fmt.Scanln(&path)
	if err := uploadInteractive(serverURL, apiKey, path); err != nil {
		notify("Upload failed: " + err.Error())
		return
	}
	notify("Uploaded: " + path)
}

func downloadDialog() {
	if !statusConnected {
		notify("Not connected. Pair first.")
		return
	}
	var name string
	fmt.Print("Remote file name: ")
	fmt.Scanln(&name)
	if err := downloadInteractive(serverURL, apiKey, name, "."); err != nil {
		notify("Download failed: " + err.Error())
		return
	}
	notify("Downloaded: " + name)
}

func toggleAutoStart() {
	// Toggle auto-start via Windows registry
	cfg.AutoStart = !cfg.AutoStart
	saveConfig(cfg)
	updateAutoStart(cfg.AutoStart)
	menuAutoStart.SetTitle(fmt.Sprintf("Auto-start on login: %v", cfg.AutoStart))
}

func updateAutoStart(enabled bool) {
	if enabled {
		menuAutoStart.Check()
	} else {
		menuAutoStart.Uncheck()
	}
}

func notify(msg string) {
	systray.SetTooltip(msg)
}

func getMAC() string {
	interfaces, err := net.Interfaces()
	if err != nil {
		return "00:00:00:00:00:00"
	}
	for _, iface := range interfaces {
		if len(iface.HardwareAddr) >= 6 && iface.Flags&net.FlagLoopback == 0 {
			return iface.HardwareAddr.String()
		}
	}
	return "00:00:00:00:00:00"
}
