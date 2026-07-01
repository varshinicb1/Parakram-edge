package main

import (
	"bytes"
	"image"
	"image/color"
	"image/png"
	"sync"
)

var iconData []byte
var iconOnce sync.Once

func getIcon() []byte {
	iconOnce.Do(func() {
		img := image.NewNRGBA(image.Rect(0, 0, 32, 32))
		blue := color.NRGBA{R: 0x42, G: 0x6e, B: 0xe2, A: 0xff}
		dark := color.NRGBA{R: 0x2a, G: 0x52, B: 0xbe, A: 0xff}
		for y := 0; y < 32; y++ {
			for x := 0; x < 32; x++ {
				cx, cy := x-16, y-22
				if cy < 0 {
					cy = -cy
				}
				dist := cx*cx + cy*cy
				if dist < 160 && y > 4 && y < 28 && x > 4 && x < 28 {
					img.Set(x, y, blue)
				} else if y > 24 && y < 30 && x > 10 && x < 22 {
					img.Set(x, y, dark)
				}
			}
		}
		var buf bytes.Buffer
		if err := png.Encode(&buf, img); err == nil {
			iconData = buf.Bytes()
		}
	})
	return iconData
}
