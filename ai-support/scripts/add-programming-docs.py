#!/usr/bin/env python3
"""Add detailed programming/graphics docs to knowledge sets."""
import requests
import json

BASE = 'http://localhost:8090'

def add_doc(ks_id, title, content):
    r = requests.post(f'{BASE}/api/knowledge/sets/{ks_id}/documents', json={
        'title': title, 'content': content, 'sourceType': 'MANUAL'
    })
    d = r.json()
    print(f'  [{d.get("id","?")}] {title} - {d.get("chunksIndexed",0)} chunks')

print('=== Adding Amiga Programming Docs ===')

add_doc(1, 'Amiga Graphics Programming', """Amiga graphics programming uses the custom chipset (Blitter, Copper, display hardware) for high-performance 2D graphics.

## Line Drawing

The Amiga Blitter has a dedicated line-drawing mode. To draw a line:

1. Set up Blitter control registers (BLTCON0, BLTCON1) with line-drawing mode bits.
2. BLTCON0: Set USE_A and USE_C, set minterm for line operation (usually 0xCA for inclusive-or).
3. BLTCON1: Set LINE bit (bit 0), set octant bits for line direction, set SING (single-bit) if desired.
4. Calculate dx = |x2-x1| and dy = |y2-y1|. The Blitter draws lines using the longer axis.
5. BLTAPT: Set to initial DDA error term: 4*dy - 2*dx (for octant 0).
6. BLTBMOD: Set to 4*dy (increment when moving along major axis only).
7. BLTAMOD: Set to 4*(dy-dx) (increment when also stepping along minor axis).
8. BLTCPT and BLTDPT: Point to the bitplane word containing starting pixel.
9. BLTSIZE: Set height = max(dx,dy)+1 and width = 2 (always 2 for line mode).

The Blitter performs Bresenham's line algorithm in hardware, making it extremely fast.

## Software Line Drawing (Bresenham's Algorithm in C)

void draw_line(struct BitMap *bm, int x0, int y0, int x1, int y1) {
    int dx = abs(x1 - x0);
    int dy = abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1;
    int sy = y0 < y1 ? 1 : -1;
    int err = dx - dy;
    while (1) {
        // Set pixel at (x0, y0) in the bitplane
        UBYTE *plane = bm->Planes[0];
        int offset = y0 * bm->BytesPerRow + (x0 >> 3);
        plane[offset] |= (0x80 >> (x0 & 7));
        if (x0 == x1 && y0 == y1) break;
        int e2 = 2 * err;
        if (e2 > -dy) { err -= dy; x0 += sx; }
        if (e2 < dx) { err += dx; y0 += sy; }
    }
}

## Using graphics.library

The OS provides line drawing through graphics.library:
    Move(rp, x0, y0);    // Move pen to start
    Draw(rp, x1, y1);    // Draw line to end
SetAPen(rp, color) sets the pen color. The RastPort handles clipping and bitplane operations.

## Circle and Ellipse Drawing

graphics.library provides DrawEllipse(rp, cx, cy, rx, ry) for ellipses.
For filled shapes, use AreaMove/AreaDraw/AreaEnd with a TmpRas for area fill.

## Double Buffering

For smooth animation:
1. Allocate two screen buffers
2. Draw to back buffer using Blitter/Move/Draw
3. Swap buffers by changing display pointer via Copper
4. Prevents tearing and flicker

## Screen Modes and Coordinates

- Low-res: 320x200 (NTSC) or 320x256 (PAL)
- High-res: 640x200 or 640x256
- Interlaced doubles vertical resolution
- AGA: Up to 256 colors from 24-bit palette at various resolutions
""")

add_doc(1, 'Amiga Blitter Programming Guide', """The Amiga Blitter is a DMA-driven hardware block transfer unit for memory copies, fills, and line drawing independent of the CPU.

## Key Blitter Registers

- BLTCON0 (0xDFF040): Control register 0 - source enables, minterms, shift value
- BLTCON1 (0xDFF042): Control register 1 - fill mode, line mode flags
- BLTAFWM/BLTALWM: First/last word masks for source A
- BLTAPT, BLTBPT, BLTCPT, BLTDPT: Source/destination data pointers
- BLTAMOD, BLTBMOD, BLTCMOD, BLTDMOD: Modulos (bytes to skip per row)
- BLTSIZE (0xDFF058): Starts the blit - encodes height and width

## Block Copy (Cookie Cut)

To copy a masked shape:
- Source A = mask, Source B = image, Source C = background, Dest D = output
- Minterm: D = (A AND B) OR (NOT A AND C) = 0xCA
- Configure modulos for source and destination widths

## Area Fill

The Blitter fills enclosed areas:
1. Draw outline using Blitter line mode
2. Set fill mode in BLTCON1 (inclusive or exclusive fill)
3. Blit the area - fills between edge pixels right-to-left per scanline
4. Extremely fast compared to CPU flood fill

## Performance

- Blitter runs concurrently with CPU (parallel processing)
- Approximately 2x faster than 68000 for large block copies
- 10x faster for line drawing
- Use OwnBlitter()/DisownBlitter() to prevent OS interruption
- Wait for completion with WaitBlit() before accessing results
""")

print()
print('=== Adding Gowin FPGA Graphics Docs ===')

add_doc(2, 'Gowin FPGA Graphics and VGA/HDMI Output', """Implementing video output and graphics on Gowin FPGAs.

## VGA Signal Generation

A VGA signal requires horizontal/vertical sync plus RGB data. For 640x480 at 60Hz you need a 25.175 MHz pixel clock.

Timing constants:
- H_DISPLAY=640, H_FRONT=16, H_SYNC=96, H_BACK=48, H_TOTAL=800
- V_DISPLAY=480, V_FRONT=10, V_SYNC=2, V_BACK=33, V_TOTAL=525

Generate pixel clock from the Tang Nano 9K's 27MHz crystal using Gowin PLL IP.

## Hardware Line Drawing (Bresenham in Verilog)

Implement Bresenham's line algorithm as a state machine:

module line_drawer(input clk, rst, start, input [9:0] x0,y0,x1,y1, output reg done, wr_en, output reg [9:0] wr_x, wr_y);
States: IDLE (wait for start), INIT (compute dx,dy,sx,sy,err), DRAW (step and write pixels), DONE.
In DRAW state: output wr_en with pixel coordinates, compute next Bresenham step.
One pixel per clock cycle for maximum throughput.

## Frame Buffer using Block RAM

Use Gowin BSRAM as frame buffer:
- 320x240 monochrome = 9,600 bytes = 76.8 Kbit (easily fits)
- 320x240 8-bit color = 76,800 bytes = needs external SDRAM
- Tang Nano 9K has 468 Kbit BSRAM total
- Use dual-port RAM: write port for drawing, read port for display scanning

## Tang Nano 9K HDMI Output

The Tang Nano 9K has built-in HDMI:
1. Generate pixel clock with Gowin PLL
2. Use OSER10 primitives for TMDS serialization
3. Feed VGA-style timing to TMDS encoder
4. Supports 640x480 and 720x480 output

## Drawing Primitives in Hardware

Build a simple GPU with command FIFO:
- LINE: x0,y0,x1,y1 - uses Bresenham module
- RECT: x,y,w,h - sequential pixel writes
- PIXEL: x,y,color - single pixel
- CLEAR: fill entire frame buffer

Each primitive is a state machine that writes to the frame buffer's write port.
""")

print()
print('=== Adding Atari Graphics Programming Docs ===')

add_doc(3, 'Atari 8-bit Graphics Programming', """The Atari 8-bit has powerful custom graphics hardware with ANTIC and GTIA chips.

## Line Drawing in Atari BASIC

    10 GRAPHICS 8          : REM 320x192 mode
    20 COLOR 1             : REM Set drawing color
    30 PLOT 10,10          : REM Start point
    40 DRAWTO 300,180      : REM Draw line to endpoint

DRAWTO uses Bresenham's algorithm internally.

## Line Drawing in 6502 Assembly

For fast lines, implement Bresenham's in assembly. Key challenge: screen memory is linear bytes where each byte holds 8 pixels (mode 8) or 4 pixels (mode 15).

To plot a pixel: calculate byte = Y * 40 + X / 8, then set bit (7 - (X mod 8)).
For Bresenham: maintain error accumulator, step X and/or Y each iteration, plot pixel at each step.
Assembly implementation runs roughly 100x faster than BASIC DRAWTO.

## Graphics Modes (ANTIC)

ANTIC provides 14 display modes:
- Mode 2 (BASIC 0): 40 chars x 24 lines, 2 colors (text)
- Mode 8 (BASIC 3): 40 chars x 24 lines, 4 colors (text)
- Mode 14 (BASIC 7): 160x192, 2 colors per line
- Mode 15 (BASIC 8): 320x192, monochrome
- Mode F (BASIC 8): High-res, 1 color + background

## Player/Missile Graphics (Sprites)

GTIA provides 4 players (8 pixels wide) and 4 missiles:
- Independent of playfield, overlay on top
- Hardware collision detection
- Move by writing to HPOSP0-3 registers
- Perfect for game characters

## Display List Interrupts (DLI)

Change colors mid-screen:
1. Set DLI flag on display list instruction
2. ANTIC triggers NMI at that scanline
3. Your routine changes COLPF/COLBK registers
4. Different colors per screen section (like Amiga Copper)

## Character Set Redefinition

Redefine character shapes for custom tiles:
- Copy ROM charset to RAM
- Modify individual character bitmaps (8 bytes each)
- Point CHBAS to your custom set
- Used extensively in games for tile-based graphics
""")

print()
print('=== Updated Stats ===')
for ks_id in [1, 2, 3]:
    r = requests.get(f'{BASE}/api/knowledge/sets/{ks_id}')
    d = r.json()
    print(f'  {d["name"]}: {d["documentCount"]} docs, {d["chunkCount"]} chunks, {d["vectorCount"]} vectors')
