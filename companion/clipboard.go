package main

import (
	"context"
	"time"
	"unicode/utf8"

	"golang.design/x/clipboard"
)

var clipboardEnabled bool
var lastClipboardText string

func initClipboard() error {
	return clipboard.Init()
}

func startClipboardSync(serverURL, apiKey string, onChange func(string)) {
	if !clipboardEnabled {
		return
	}
	lastClipboardText = string(clipboard.Read(clipboard.FmtText))

	go func() {
		ch := clipboard.Watch(context.Background(), clipboard.FmtText)
		for data := range ch {
			text := string(data.Bytes)
			if text != lastClipboardText && text != "" {
				lastClipboardText = text
				if err := pushClipboard(serverURL, apiKey, text); err == nil {
					onChange("Pushed to phone: " + truncate(text, 50))
				}
			}
		}
	}()

	go func() {
		for range time.Tick(3 * time.Second) {
			remote, err := pullClipboard(serverURL, apiKey)
			if err != nil || remote == "" {
				continue
			}
			if remote != lastClipboardText {
				lastClipboardText = remote
				clipboard.Write(clipboard.FmtText, []byte(remote))
				onChange("Pulled from phone: " + truncate(remote, 50))
			}
		}
	}()
}

func truncate(s string, n int) string {
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	runes := []rune(s)
	return string(runes[:n]) + "..."
}
