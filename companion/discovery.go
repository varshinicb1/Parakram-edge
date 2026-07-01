package main

import (
	"fmt"
	"net"
	"strings"
	"time"
)

type DeviceInfo struct {
	IP       string
	Port     int
	Name     string
	ServerID string
}

func discoverDevices(timeout time.Duration) ([]DeviceInfo, error) {
	conn, err := net.ListenMulticastUDP("udp4", nil, &net.UDPAddr{IP: net.ParseIP("224.0.0.251"), Port: 5353})
	if err != nil {
		return nil, fmt.Errorf("mDNS listen: %w", err)
	}
	defer conn.Close()

	query := []byte(buildMDNSQuery("_remix._tcp.local"))
	dest := &net.UDPAddr{IP: net.ParseIP("224.0.0.251"), Port: 5353}
	conn.SetReadDeadline(time.Now().Add(timeout))

	var devices []DeviceInfo
	seen := make(map[string]bool)

	for attempt := 0; attempt < 3; attempt++ {
		conn.WriteTo(query, dest)
		for {
			buf := make([]byte, 1500)
			n, _, err := conn.ReadFromUDP(buf)
			if err != nil {
				break
			}
			dev := parseMDNSResponse(buf[:n])
			if dev != nil {
				key := fmt.Sprintf("%s:%d", dev.IP, dev.Port)
				if !seen[key] {
					seen[key] = true
					devices = append(devices, *dev)
				}
			}
		}
	}

	return devices, nil
}

func buildMDNSQuery(service string) string {
	var parts []string
	for _, label := range strings.Split(service, ".") {
		if label == "" {
			continue
		}
		parts = append(parts, string(rune(len(label)))+label)
	}
	parts = append(parts, "\x00")

	query := ""
	for _, p := range parts {
		query += p
	}
	query += "\x00\x00\x0c\x00\x01" // QTYPE=PTR, QCLASS=IN
	return query
}

func parseMDNSResponse(data []byte) *DeviceInfo {
	if len(data) < 12 {
		return nil
	}

	offset := 12
	var dev DeviceInfo

	for offset < len(data) {
		if data[offset] == 0xC0 {
			offset += 2
		} else {
			for offset < len(data) && data[offset] != 0 {
				offset++
			}
			offset++
		}

		if offset+10 > len(data) {
			break
		}

		qtype := int(data[offset])<<8 | int(data[offset+1])
		offset += 10

		if qtype == 16 { // TXT record
			txtLen := int(data[offset])<<8 | int(data[offset+1])
			offset += 2
			txtData := data[offset : offset+txtLen]
			offset += txtLen
			parseTXTRecord(txtData, &dev)
		} else if qtype == 1 { // A record
			offset += 2
			rdlength := int(data[offset])<<8 | int(data[offset+1])
			offset += 2
			if rdlength == 4 && offset+4 <= len(data) {
				dev.IP = net.IPv4(data[offset], data[offset+1], data[offset+2], data[offset+3]).String()
				offset += 4
			} else {
				offset += rdlength
			}
		} else if qtype == 12 || qtype == 33 { // PTR or SRV
			offset += 2
			rdlength := int(data[offset])<<8 | int(data[offset+1])
			offset += 2
			if qtype == 33 && rdlength >= 6 { // SRV: port at bytes 4-5
				dev.Port = int(data[offset+4])<<8 | int(data[offset+5])
			}
			offset += rdlength
		} else {
			offset += 2
			rdlength := int(data[offset])<<8 | int(data[offset+1])
			offset += 2 + rdlength
		}
	}

	if dev.IP == "" || dev.Port == 0 {
		return nil
	}
	return &dev
}

func parseTXTRecord(data []byte, dev *DeviceInfo) {
	for i := 0; i < len(data); {
		if i+1 > len(data) {
			break
		}
		length := int(data[i])
		i++
		if i+length > len(data) {
			break
		}
		entry := string(data[i : i+length])
		i += length

		if strings.HasPrefix(entry, "id=") {
			dev.ServerID = strings.TrimPrefix(entry, "id=")
		} else if strings.HasPrefix(entry, "name=") {
			dev.Name = strings.TrimPrefix(entry, "name=")
		}
	}
}
