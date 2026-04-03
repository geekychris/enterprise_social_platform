#!/usr/bin/env node

const BASE = 'http://localhost:8090';

async function addDoc(ksId, title, content) {
  const res = await fetch(`${BASE}/api/knowledge/sets/${ksId}/documents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, content, sourceType: 'MANUAL' }),
  });
  const data = await res.json();
  console.log(`  [${data.id ?? '?'}] ${title} - ${data.chunksIndexed ?? 0} chunks indexed`);
}

// ---------------------------------------------------------------------------
// Knowledge Set 1: Amiga Computer Support
// ---------------------------------------------------------------------------

const amigaDocs = [
  {
    title: 'Getting Started with Amiga',
    content: `The Amiga is a family of personal computers introduced by Commodore in 1985. Known for its advanced multimedia capabilities, the Amiga was years ahead of its competition in graphics, sound, and multitasking.

## Key Models

**Amiga 1000 (1985):** The original Amiga. Shipped with 256 KB of RAM (expandable to 512 KB), the OCS chipset, and a 7.16 MHz Motorola 68000 CPU. Historically significant but relatively rare today.

**Amiga 500 (1987):** The best-selling Amiga model. A compact all-in-one design with the CPU and disk drive built into the keyboard housing. Shipped with 512 KB of chip RAM, expandable to 1 MB via the trapdoor expansion. Uses Kickstart 1.2 or 1.3 in ROM.

**Amiga 2000 (1987):** A desktop/tower model aimed at professionals. Features Zorro II expansion slots and a CPU slot for accelerator cards. Can accept bridgeboard cards for PC compatibility. Highly expandable.

**Amiga 3000 (1990):** Introduced the ECS chipset and a 68030 CPU running at 25 MHz. Features Zorro III 32-bit expansion slots and built-in SCSI controller. Shipped with AmigaOS 2.0, a major OS upgrade with the new Workbench 2.0 UI.

**Amiga 1200 (1992):** A compact model with the AGA chipset, 68020 CPU at 14 MHz, and 2 MB of chip RAM. Features a PCMCIA slot and a trapdoor expansion slot for accelerators. The most popular Amiga for hobbyists today.

**Amiga 4000 (1992):** The flagship Amiga. Available with 68030 or 68040 CPUs, AGA chipset, Zorro III slots, and IDE hard drive interface. The most powerful stock Amiga produced.

**Amiga CD32 (1993):** A 32-bit CD-ROM-based game console built on A1200 hardware. The first 32-bit CD console on the market.

## AmigaOS and Workbench

AmigaOS is a preemptive multitasking operating system consisting of two main components: Kickstart (the ROM-based kernel and core libraries) and Workbench (the graphical desktop environment).

- **Kickstart 1.3** — The most common ROM for the A500. Blue hand-insert-disk screen.
- **Kickstart 2.0 (37.175)** — Introduced with the A3000. Major rewrite of the OS. New 3D-style UI.
- **Kickstart 3.0 (39.106)** — Shipped with A1200 and A4000. Supports AGA chipset.
- **Kickstart 3.1 (40.68)** — The final Commodore release. The gold standard for classic Amiga OS.
- **Kickstart 3.1.4 / 3.2** — Modern updates by Hyperion Entertainment. Adds bug fixes, new features, and support for modern hardware expansions.

Workbench is the Amiga's GUI desktop. It uses an icon-driven interface with pull-down menus. Files and directories are represented as drawers and icons. The Workbench disk also contains essential tools like the Shell (CLI), preferences editors, and system utilities.

## Getting Help

When asking for Amiga support, always mention your specific model, Kickstart version, installed RAM, and any accelerator or expansion cards. This information is critical for diagnosing issues and recommending compatible software and hardware.`,
  },
  {
    title: 'Amiga Hardware Troubleshooting',
    content: `Amiga computers are now 30-40 years old, and certain hardware failures are extremely common. This guide covers the most frequent issues and their solutions.

## Bad Capacitors (Capacitor Plague)

This is the single most common issue with Amiga computers, especially the A500, A600, A1200, and A4000. Electrolytic capacitors used in these machines have a limited lifespan and leak corrosive electrolyte onto the PCB over time.

**Symptoms:** Garbled or no audio, distorted video, intermittent crashes, total failure to boot, visible brown/green residue on the motherboard around capacitor legs.

**Solution:** Full capacitor replacement ("recap") is essential. For the A1200, there are approximately 10 electrolytic caps on the board. Use high-quality replacements (Panasonic, Nichicon, or Rubycon). Many enthusiasts prefer SMD tantalum or polymer capacitors as modern replacements. Before recapping, clean all leaked electrolyte with isopropyl alcohol and a soft brush. Check for damaged traces — leaked electrolyte eats through copper traces over time.

**Critical caps:** On the A1200, C235 and nearby caps near the audio circuit are notorious. On the A500, the barrel-style axial caps near the audio filter are common failure points. The A4000 has an especially severe recapping requirement due to its dense board layout.

## Floppy Drive Problems

**Symptoms:** Drive clicks repeatedly, won't read disks, read errors, or no spin-up.

**Common causes:**
- Dirty or misaligned read/write heads — clean with isopropyl alcohol on a cotton swab
- Worn or stretched drive belt (especially on A500/A2000 drives) — replace the belt
- Failed capacitors on the drive's small PCB
- Disk sensor issues — the disk-detect switches can become dirty or bent

The standard Amiga floppy drive reads double-density (DD) 880 KB disks. HD (high-density) disks can sometimes be used by covering the HD hole with tape, but this is unreliable.

## Display Issues

**No video output:** Check the Kickstart ROM is properly seated. A continuous grey or black screen usually indicates a ROM, custom chip, or RAM failure. Use a diagnostic tool like DiagROM to test.

**Garbled graphics:** Usually indicates a failing Denise/Lisa (video) chip, bad RAM, or corroded connections. On AGA machines, the Lisa chip is particularly sensitive.

**Wrong colors or no color:** On the A500/A2000, check the video DAC resistor network. For composite/RF output, the modulator may have failed.

## Power Supply Failures

The original Amiga power supplies (especially A500 "brick" PSUs) are known to fail and can damage the computer when they do. They can output excessive voltage when failing.

**Recommendation:** Replace the original PSU with a modern replacement. Several vendors sell modern switching PSUs for Amiga (Electroware, Keelog, Individual Computers). These are safer, cooler, and more efficient. Always check voltages with a multimeter before connecting an old PSU: you should see +5V and +12V within 5% tolerance.

## CIA Chip Failures

The two 8520 CIA (Complex Interface Adapter) chips handle I/O for the keyboard, serial, parallel, floppy, and joystick ports. Failure symptoms include: dead keyboard, non-functional mouse/joystick ports, or floppy drive issues. CIA chips are socketed on most models and can be replaced.

## Custom Chip Failures

Agnus/Alice (DMA controller), Denise/Lisa (video), and Paula (audio/floppy) are the core custom chips. Failures typically require donor chips from another machine. On the A1200, the Lisa chip runs hot and benefits from a small heatsink.`,
  },
  {
    title: 'Installing AmigaOS',
    content: `This guide covers installing AmigaOS 3.1 and 3.2 on classic Amiga hardware with a hard drive.

## Prerequisites

- An Amiga with a hard drive (IDE/SCSI) or CF card adapter
- The correct Kickstart ROM for your target OS version
- AmigaOS install floppy disks (or ADF images if using a floppy emulator like Gotek)
- A way to transfer files (CF card reader, networking, or serial transfer)

## Kickstart ROM Installation

AmigaOS requires a matching Kickstart ROM. For AmigaOS 3.1, you need Kickstart 3.1 (40.68). For AmigaOS 3.2, you need Kickstart 3.2 (47.111) — though 3.2 can also run on 3.1 ROMs with reduced functionality.

**Physical ROM replacement:** Power off and unplug the Amiga. Locate the Kickstart ROM chip(s) on the motherboard. The A1200 uses a single 40-pin ROM. The A500 uses a single 40-pin ROM in a socket. Carefully remove the old ROM with a chip puller, noting orientation (pin 1 marker). Insert the new ROM ensuring correct alignment. Some models need an adapter for different ROM sizes.

**Flash ROM options:** Devices like the Individual Computers ROMinator or similar allow you to flash multiple Kickstart versions to a single chip and select between them.

## Preparing the Hard Drive

1. Boot from the Install disk (or Workbench disk if using floppies).
2. Open the **HDToolBox** utility from the Tools drawer. This is the Amiga's native partitioning tool.
3. Select your drive and create partitions:
   - **DH0:** (System) — 500 MB is plenty for a system partition. Set this as bootable with a boot priority of 0.
   - **DH1:** (Work/Data) — Use remaining space.
4. Set the filesystem to **Fast File System (FFS)** for each partition. Enable the **International Mode** and optionally **Directory Cache** flags (though directory cache can cause issues with some tools).
5. Set the MaxTransfer value to 0x1FE00 for IDE drives to avoid DMA transfer issues.
6. Save changes and reboot.

## Formatting Partitions

After creating partitions, format each one using the Workbench Format command or from the Shell:

\`\`\`
format drive DH0: name System ffs intl quick
format drive DH1: name Work ffs intl quick
\`\`\`

## Installing the OS

1. Insert the Install disk and boot the Amiga.
2. The Installer will launch. Select your language and destination partition (DH0:).
3. Follow the prompts. The installer will ask you to insert various disks (Workbench, Locale, Extras, Fonts, Storage).
4. For a minimal install, you need at least the Workbench and Extras disks.
5. After installation, the system will be bootable from the hard drive.

## Post-Installation Setup

After a base OS install, consider adding these essential components:

- **ClassAct/ReAction** — GUI toolkit used by many modern Amiga applications
- **MUI (Magic User Interface)** — An alternative, very popular GUI toolkit
- **ixemul.library** — Unix compatibility library needed by many ported applications
- **AmiSSL** — SSL/TLS support for networking applications
- **The Amiga OS 3.2 update** patches (if running 3.2), available from Hyperion

## Using a CF Card

Modern Amiga users typically replace the hard drive with a CompactFlash card via a 44-pin IDE-to-CF adapter. CF cards are silent, fast, low-power, and easy to image. Use a 4-16 GB industrial-grade CF card for best compatibility. You can prepare a CF card on a PC using tools like WinUAE (create a virtual hard drive, install the OS in emulation, then write the HDF image to the CF card).`,
  },
  {
    title: 'Amiga Emulation with WinUAE and FS-UAE',
    content: `Amiga emulation allows you to run AmigaOS and Amiga software on modern computers. The two most popular emulators are WinUAE (Windows) and FS-UAE (cross-platform).

## WinUAE

WinUAE is the most accurate and feature-rich Amiga emulator available, developed primarily by Toni Wilen. It runs on Windows and can emulate virtually every Amiga model with extreme accuracy.

**Setup steps:**
1. Download WinUAE from https://www.winuae.net
2. Obtain legal Kickstart ROM files. You need the ROM matching the Amiga model you want to emulate. Amiga Forever from Cloanto includes licensed ROM files. Alternatively, you can dump ROMs from your own Amiga using tools like TransROM.
3. Launch WinUAE and go to Paths — set your ROM path, floppy disk path, and hard drive path.
4. Choose a Quickstart configuration (e.g., A500, A1200, A4000) or build a custom config.
5. Under Hardware > ROM, select the appropriate Kickstart ROM.
6. Under Hardware > RAM, set chip RAM, slow RAM, and fast RAM amounts.

**Hard drive emulation:** WinUAE can use HDF files (hard drive image files) or mount a directory on your PC as an Amiga hard drive. Directory-based hard drives are convenient for file exchange.

**Graphics options:** WinUAE supports multiple rendering modes including line doubling, scanline emulation, shader support, and custom scaling. The Direct3D 11 output mode provides the best performance and visual quality.

## FS-UAE

FS-UAE is an open-source, cross-platform Amiga emulator based on the UAE emulation core. It runs on Windows, macOS, and Linux. FS-UAE focuses on ease of use with a clean launcher UI.

**Setup steps:**
1. Download FS-UAE from https://fs-uae.net
2. Place Kickstart ROMs in the designated ROMs folder (~/Documents/FS-UAE/Kickstarts on most platforms).
3. FS-UAE Launcher provides a game database and can auto-configure settings when it recognizes a game.
4. Create configurations in the Launcher specifying the Amiga model, ROM, floppy disks, and hard drives.

FS-UAE is particularly good for gaming due to its built-in gamepad support and fullscreen-first design.

## WHDLoad

WHDLoad is an essential tool for running Amiga games and demos from a hard drive (or emulated hard drive) instead of floppy disks. Most classic Amiga games were designed to boot directly from floppies and bypass the operating system. WHDLoad provides a framework that patches these programs to run under AmigaOS.

**Using WHDLoad in emulation:**
1. Install AmigaOS on a virtual hard drive in your emulator.
2. Install WHDLoad (copy the WHDLoad binary and libraries to your system).
3. Download WHDLoad game/demo "slaves" — these are patches specific to each title.
4. Each slave comes with installation instructions. Typically, you place the slave file and the original game data files in a directory.
5. Run the slave's icon or execute it from the Shell.

WHDLoad requires a registered keyfile for full functionality (quit gracefully on exit). Without a keyfile, it works but reboots the system on exit.

## ROM Files Reference

Common Kickstart ROM filenames and their checksums:
- **Kickstart 1.3 (A500):** kick34005.A500 — CRC32: C4F0F55F
- **Kickstart 2.04 (A500+):** kick37175.A500 — CRC32: C3BDB240
- **Kickstart 3.0 (A1200):** kick39106.A1200 — CRC32: 6C9B07D2
- **Kickstart 3.1 (A1200):** kick40068.A1200 — CRC32: 1483A091
- **Kickstart 3.1 (A4000):** kick40068.A4000 — CRC32: 9E6AC152

## Performance Tips

- Enable JIT (Just-In-Time) compilation for 68020+ emulation to dramatically improve performance.
- Use cycle-exact mode only when accuracy is needed — it is significantly slower.
- For gaming, A500 with 1 MB chip RAM and Kickstart 1.3 provides the widest compatibility.
- For productivity software, emulate an A1200 or A4000 with generous RAM (8-64 MB fast RAM).`,
  },
  {
    title: 'Amiga Networking',
    content: `Setting up networking on a classic Amiga allows you to browse the web, transfer files, and access modern services. While the Amiga was not originally designed for TCP/IP networking, several solutions exist.

## TCP/IP Stacks

**Roadshow** is the recommended TCP/IP stack for AmigaOS 3.x. It is a commercial, actively maintained product that provides a full BSD-socket-compatible TCP/IP implementation. Roadshow supports DHCP, DNS, and integrates well with modern Amiga networking software.

**AmiTCP** is a free, older TCP/IP stack. Genesis is a derivative of AmiTCP with a graphical configuration interface. Both work but are no longer actively developed.

**Miami** and **MiamiDx** were popular commercial stacks in the late 1990s but are now abandonware.

## Network Hardware Options

**PCMCIA Ethernet (A1200/A600):** The most common solution for A1200 networking. Cards like the Ariadne II PCMCIA or various NE2000-compatible PCMCIA cards work with appropriate drivers. The PCMCIA slot on the A1200 provides a convenient way to add Ethernet without opening the case.

**Zorro Ethernet (A2000/A3000/A4000):** Zorro II and III network cards include the Ariadne, Ariadne II (Zorro version), X-Surf, and X-Surf 100. The X-Surf 100 by Individual Computers is a modern production Zorro II/III Fast Ethernet card providing 100 Mbit speeds.

**USB Ethernet (via accelerators):** Some modern accelerator cards (like the Vampire or PiStorm) can support USB networking through software drivers. This is an evolving area.

**WiFi via bridge:** A practical approach is to use a WiFi-to-Ethernet bridge device. Connect a cheap TP-Link wireless bridge or a Raspberry Pi configured as a WiFi bridge to the Amiga's Ethernet port. The Amiga sees only a standard Ethernet connection.

**Serial PPP/SLIP:** The slowest option — connecting via the Amiga's serial port to another computer running a PPP server. Limited to about 115200 baud (roughly 11 KB/s). Useful as a last resort.

## Setting Up Roadshow

1. Install Roadshow to your system partition.
2. Configure the network interface by editing the DEVS:NetInterfaces directory. Create a file for your interface, e.g., "eth0":
   \`\`\`
   device=pcnet.device
   unit=0
   configurator=dhcp
   \`\`\`
3. For static IP, replace the configurator line:
   \`\`\`
   address=192.168.1.100
   netmask=255.255.255.0
   \`\`\`
4. Set your default gateway and DNS in DEVS:Internet/name_resolution:
   \`\`\`
   DOMAIN=local
   NAMESERVER=192.168.1.1
   \`\`\`
5. Start the stack: \`AddNetInterface eth0\` from the Shell.
6. Test with: \`ping 8.8.8.8\`

## Networking Software

Once TCP/IP is running, you can use:

- **IBrowse** — A native Amiga web browser. Supports basic HTML and JavaScript. Limited CSS support. Best for simple websites.
- **NetSurf** — A modern, lightweight browser ported to Amiga. Better standards support than IBrowse.
- **AmiTradeCenter (ATC)** — A native FTP client.
- **YAM (Yet Another Mailer)** — A popular email client supporting POP3 and SMTP.
- **AmIRC** — An IRC client for real-time chat on IRC networks.
- **smbfs** — An SMB/CIFS client for accessing Windows shared folders.
- **nfs** — NFS client support for mounting Unix/Linux network shares.

## File Transfer Without Networking

If networking is impractical, other options exist:
- **Null-modem serial cable** with terminal software (JRComm, Term) or file transfer protocols (Zmodem)
- **CF card** — Remove the CF card from the Amiga, mount it on a PC using a card reader, and copy files directly. The Amiga partitions use the RDB (Rigid Disk Block) format, readable on Linux and with special tools on Windows.
- **Floppy transfer** — Write ADF images to real floppies using a Kryoflux or Greaseweazle device.`,
  },
  {
    title: 'Amiga Memory Upgrades',
    content: `Memory is one of the most important upgrades for an Amiga. Different types of memory serve different purposes in the Amiga architecture.

## Memory Types

**Chip RAM:** Memory accessible by both the CPU and the custom chips (Agnus/Alice, Denise/Lisa, Paula). All graphics, sound, and DMA operations use chip RAM. The amount of chip RAM depends on the Agnus/Alice chip version:
- OCS Agnus (A500/A2000): 512 KB chip RAM (Fat Agnus: 1 MB)
- ECS Agnus (A500+/A600/A3000): 1 MB or 2 MB chip RAM
- AGA Alice (A1200/A4000/CD32): 2 MB chip RAM

**Slow RAM (Ranger/Bogo RAM):** Found on the A500 trapdoor expansion. Accessible at the same speed as chip RAM but NOT accessible to the custom chips. Typically 512 KB. On the A500, this gives you 1 MB total (512 KB chip + 512 KB slow).

**Fast RAM:** Accessible only by the CPU, with no contention from custom chips. This is where you want your programs to run for maximum performance. Fast RAM can be added via Zorro slots, accelerator cards, or specific expansion connectors.

## A500 Memory Upgrades

- **Trapdoor expansion:** The bottom expansion slot accepts 512 KB slow RAM boards. Many clones exist. Must have the real-time clock option if you want battery-backed time keeping.
- **Fat Agnus upgrade:** Replace the original Agnus with a Fat Agnus (8372A) to get 1 MB of chip RAM. This also requires a minor PCB modification (connecting the A19 address line).
- **Accelerator cards:** CPU accelerator cards for the 68000 socket typically include fast RAM. For example, the A530 turbo provides a 68030 CPU and up to 8 MB of fast RAM.

## A1200 Memory Upgrades

- **Trapdoor RAM:** The A1200's trapdoor accepts a memory expansion board on the 150-pin connector. Classic options include the Blizzard 1220/4 (4 MB fast RAM + FPU), Blizzard 1230/50 (68030 + up to 64 MB), and Blizzard 1260 (68060 + up to 64 MB).
- **Modern alternatives:** The ACA1233n (Individual Computers) provides a 68030 at 40 MHz with 128 MB of fast RAM in a compact form factor.

## Modern Accelerator Cards

**Vampire (Apollo Team):** The Vampire is an FPGA-based accelerator that replaces the original CPU with a much faster "Apollo 68080" core. Available for multiple Amiga models:
- Vampire V2 (A500/A600/A1200) — plugs into the CPU socket. Provides 68080 CPU at 85+ MHz equivalent, 128 MB fast RAM, HDMI output, SD card slot, Ethernet, and USB.
- Vampire V4 Standalone — A standalone Amiga-compatible computer. 512 MB RAM, HDMI, USB, SD card, Ethernet.

The Vampire adds RTG (Retargetable Graphics) support via SAGA, allowing high-resolution true-color displays via HDMI.

**PiStorm:** An open-source project that uses a Raspberry Pi as an Amiga accelerator. A small adapter board plugs into the 68000 CPU socket and connects to a Raspberry Pi 3B+/4.

Features:
- Emulated 68020/68030/68040 with configurable clock speed (hundreds of MHz effective)
- Up to 256 MB of fast RAM
- RTG graphics output via the Pi's HDMI
- Network access via the Pi's WiFi/Ethernet
- SD card mass storage

PiStorm runs Musashi (a 68000 emulator) on the Pi and translates bus access to/from the Amiga's system bus. It's remarkably effective and very affordable (around $20 for the adapter board plus the cost of a Raspberry Pi).

**TF536 / TF1260:** The TerribleFire accelerator cards are open-source hardware designs. The TF536 provides a 68030 at 50 MHz with up to 64 MB fast RAM and IDE interface for the A500.

## Memory Configuration Tips

- Always ensure your power supply can handle the increased power draw from memory expansions.
- Some software (particularly games) may not work correctly with more than a few MB of fast RAM. Use tools like NoFastMem or the Early Startup Menu (hold both mouse buttons at boot) to disable fast RAM for compatibility.
- For the best experience with Workbench and productivity software, 8 MB of fast RAM is a comfortable minimum; 32-64 MB is ideal.`,
  },
  {
    title: 'Amiga Sound and Graphics',
    content: `The Amiga's custom chipset provided multimedia capabilities far ahead of its time. Understanding the chipset is key to getting the most from your Amiga.

## Custom Chipsets

**OCS (Original Chip Set):** The first Amiga chipset, found in the A1000, early A500, and A2000. Consists of:
- **Agnus** (8361/8367) — DMA controller and blitter. Controls memory access scheduling for all chips.
- **Denise** (8362) — Video output. Generates the display from bitplane data. Supports 32 colors from a palette of 4,096, or 4,096 colors in HAM (Hold-And-Modify) mode. Maximum resolution: 640x512 interlaced.
- **Paula** (8364) — Audio output and floppy disk controller. Four independent 8-bit PCM audio channels with independent volume and frequency control.

**ECS (Enhanced Chip Set):** Found in the A500+, A600, and A3000. Includes:
- **Super Agnus** (8375) — Supports up to 2 MB chip RAM. Adds Super Hires mode (1280x512).
- **Super Denise** (8373) — Adds Productivity mode (flicker-free 640x480), improved sprite support.
- **Paula** — Unchanged from OCS.

**AGA (Advanced Graphics Architecture):** Found in the A1200, A4000, and CD32:
- **Alice** — Enhanced Agnus supporting 2 MB chip RAM with improved DMA bandwidth.
- **Lisa** — Enhanced Denise. Supports 256 colors from a 24-bit palette (16.7 million colors), 8 bitplanes, HAM8 mode (262,144 simultaneous colors), and resolutions up to 1280x512. Sprite resolution doubled.
- **Paula** — Still unchanged. Audio is the same 4-channel 8-bit output.

## Screen Modes

The Amiga supports multiple screen modes, each with different resolutions, color depths, and timing:

- **NTSC Low Res:** 320x200, up to 32 colors (5 bitplanes). The standard for most games.
- **PAL Low Res:** 320x256, up to 32 colors. PAL gives more vertical resolution.
- **High Res:** 640x200 (NTSC) or 640x256 (PAL), up to 16 colors (4 bitplanes).
- **Interlaced:** Doubles vertical resolution (e.g., 320x400, 640x512) but causes visible flicker on CRT displays.
- **Super Hires (ECS/AGA):** 1280x200/256, limited colors.
- **HAM (Hold-And-Modify):** A unique Amiga mode. HAM6 (OCS/ECS) displays 4,096 colors; HAM8 (AGA) displays 262,144 colors. Works by modifying one color component per pixel relative to the previous pixel. Causes fringing artifacts on sharp color boundaries.
- **AGA modes:** 320x256 or 640x512 with up to 256 colors (8 bitplanes).

## Audio System

Paula provides four DMA-driven 8-bit PCM audio channels. Channels 0 and 3 output to the left speaker; channels 1 and 2 output to the right. Each channel has:
- Independent frequency (period) control — sample rates up to ~28 kHz at standard DMA
- Independent volume (0-64)
- Hardware audio modulation — one channel can modulate the period or volume of the next

The audio output passes through a low-pass filter (the "Amiga audio filter" or "LED filter" on A500/A1200 — toggled by the power LED brightness). The filter rolls off frequencies above approximately 3.3 kHz, giving the Amiga its characteristic warm sound. Many users prefer to disable the filter for crisper audio.

**Audio output connections:**
- A500/A1200: RCA phono jacks (left and right stereo)
- A2000/A3000/A4000: RCA phono jacks
- For best quality, connect directly to powered speakers or an amplifier

## RTG (Retargetable Graphics)

RTG systems bypass the native Amiga chipset to provide modern graphics output via graphics cards or FPGA-based solutions. RTG supports high resolutions (1920x1080 and beyond) with 16-bit or 24-bit color. Systems include:
- **Picasso96 (P96)** — The most widely used RTG system. Supports numerous graphics cards.
- **CyberGraphX (CGX)** — An alternative RTG system.
- RTG is essential for modern Amiga usage with the Vampire's SAGA output or PiStorm's Pi HDMI output.`,
  },
  {
    title: 'Amiga Software',
    content: `The Amiga has a rich software library spanning creativity tools, productivity applications, games, and system utilities. Here are the most important applications every Amiga user should know about.

## Graphics and Art

**Deluxe Paint (DPaint):** The iconic Amiga paint program by Electronic Arts, created by Dan Silva. Versions I through V were released. DPaint V (AGA) supports all AGA screen modes and up to 256 colors. Features include animation painting, color cycling, perspective mode, and stencils. DPaint defined pixel art workflows for a generation and was used professionally in game development (many 16-bit era games used DPaint for sprite and background art).

**Personal Paint (PPaint):** An advanced paint program that rivaled DPaint, particularly strong in animation features and AGA support. Supports Anim5/7/8 formats.

**ImageFX:** A professional image processing application similar to Photoshop. Supports 24-bit true-color images, layers, effects, and scanning. Was used professionally in video production.

**Brilliance:** Another capable paint and animation program with strong HAM8 support.

## Music and Audio

**ProTracker:** The definitive Amiga music tracker. A four-channel MOD music editor that became the foundation of the entire tracker music scene. ProTracker uses sampled instruments triggered by pattern-based sequencing. The MOD format it popularized is still widely supported today. ProTracker 2.3D is the classic reference version.

**OctaMED:** An extended tracker supporting up to 8 channels (through software mixing or audio channel splitting). Also supports MIDI output for controlling external synthesizers.

**Bars & Pipes Professional:** A full-featured MIDI sequencer for professional music production. Supported real-time MIDI processing with a visual "pipes" metaphor for signal routing.

## Productivity

**Directory Opus (DOpus):** The premier file manager for the Amiga. Far more than a simple file browser — DOpus provides a dual-pane interface with extensive file operations, archive handling (LHA, LZX, ZIP), FTP support, viewers for images and text, and a powerful button bank system. DOpus 4 is the classic version; DOpus 5/Magellan replaces Workbench entirely as a desktop environment.

**Final Writer:** A WYSIWYG word processor with graphics integration, spell checking, and print formatting. The Amiga's answer to Microsoft Word.

**TurboCalc:** A powerful spreadsheet application with charting, macros, and ARexx support.

**MUI (Magic User Interface):** A comprehensive object-oriented GUI toolkit by Stefan Stuntz. MUI provides a consistent, themeable interface framework. Many modern Amiga applications require MUI. It features customizable look-and-feel, built-in context menus, and extensive widget support.

## System Utilities

**ARexx:** A built-in scripting language (REXX implementation) that serves as the Amiga's inter-process communication system. Many Amiga applications expose ARexx ports, allowing automation and integration between programs.

**Installer:** The standard Amiga software installation framework. Uses a scripting language to guide users through installation with different skill levels (Novice, Average, Expert).

**Commodore Exchange (CX):** Commodity programs that run in the background providing system enhancements — mouse acceleration, screen blankers, hot keys, etc.

## Modern Software

The Amiga community continues to develop new software:

- **AmiKit** — A pre-configured AmigaOS environment with hundreds of modern applications, themes, and enhancements.
- **AmigaOS 3.2** — The latest classic AmigaOS release with new utilities, updated preferences, and improved file system support.
- **Icaros Desktop** — A full AROS (open-source AmigaOS-compatible OS) distribution.
- **Wayfarer** — A modern web browser for 68k Amiga with CSS support.
- **Protrekkr** — A modern tracker music program inspired by ProTracker.

## Games

The Amiga's game library is legendary. Essential titles include: Lemmings, Shadow of the Beast, Turrican II, Sensible Soccer, Cannon Fodder, Worms, Monkey Island series, Another World, Speedball 2, Syndicate, Civilization, and hundreds more. Most games can be run from hard drive using WHDLoad.`,
  },
];

// ---------------------------------------------------------------------------
// Knowledge Set 2: Gowin FPGA Support
// ---------------------------------------------------------------------------

const gowinDocs = [
  {
    title: 'Gowin FPGA Overview',
    content: `Gowin Semiconductor is a Chinese fabless semiconductor company specializing in programmable logic devices. Founded in 2014, Gowin has rapidly become a notable player in the FPGA market, offering cost-effective FPGAs that compete with entry-level and mid-range offerings from Xilinx (AMD) and Intel (Altera).

## Product Families

**GW1N Series (LittleBee):** The entry-level family. Small, low-power FPGAs suitable for glue logic replacement, simple interfaces, and IoT applications.
- GW1N-1: ~1,152 LUTs, 1 PLL, up to 48 user I/O
- GW1N-4: ~4,608 LUTs, 2 PLLs, up to 86 user I/O
- GW1N-9: ~8,640 LUTs, 2 PLLs, up to 176 user I/O

**GW1NR Series:** GW1N variants with integrated SDRAM (64 Mbit). This is particularly attractive for projects needing RAM without extra PCB space. Used in the popular Tang Nano 9K board.

**GW1NS Series (LittleBee with ARM):** Integrates a Cortex-M3 hard processor core alongside FPGA fabric. Enables mixed hardware/software designs without a separate microcontroller.

**GW2A Series (Arora):** Mid-range family with significantly more resources.
- GW2A-18: ~20,736 LUTs, 4 PLLs, up to 608 user I/O, LVDS support, multiple BSRAM blocks
- GW2A-55: ~55,296 LUTs, 6 PLLs, DSP macros for signal processing

**GW2AR Series:** GW2A variants with integrated SDRAM. The GW2AR-18 is used in the Tang Nano 20K board.

**GW5A Series:** High-end family with features targeting video processing and communications. Larger logic capacity, high-speed transceivers, and more DSP resources.

## Comparison with Competitors

Gowin FPGAs occupy a similar market segment to Lattice Semiconductor (iCE40, ECP5) and entry-level Xilinx (Spartan-7). Key advantages:

- **Cost:** Gowin FPGAs are significantly cheaper. The GW1N-9 costs roughly $2-5 in quantity.
- **Integrated SDRAM:** The GW1NR/GW2AR variants with built-in SDRAM eliminate the need for external memory chips, reducing BOM cost and PCB complexity.
- **Low power consumption:** The LittleBee family is competitive with Lattice iCE40 for low-power applications.
- **Free development tools:** The Gowin EDA (IDE) is free for most devices, unlike Xilinx Vivado which requires paid licenses for larger devices.

**Limitations compared to Xilinx/Intel:**
- Smaller community and ecosystem
- Fewer IP cores available
- Less comprehensive documentation (though improving)
- Limited third-party tool support (though Yosys/nextpnr open-source flow is available for some devices)

## Architecture Features

- **LUT4 architecture:** Gowin FPGAs use 4-input lookup tables as the basic logic element, grouped into Configurable Function Units (CFUs).
- **Block SRAM (BSRAM):** Dedicated block RAM modules, typically 18 Kbit each, configurable as single-port, dual-port, or pseudo-dual-port memory.
- **Shadow SRAM (SSRAM):** Distributed RAM using LUT resources.
- **DSP macros:** Hardware multiplier and accumulator blocks for signal processing. Available in GW2A and larger families.
- **PLLs:** Phase-locked loops for clock generation, multiplication, and division.
- **I/O standards:** LVCMOS, LVTTL, LVDS (on GW2A+), SSTL, HSTL for DDR memory interfaces.
- **Configuration:** Supports JTAG programming, SPI flash boot, and MSPI (master SPI) configuration modes.`,
  },
  {
    title: 'Getting Started with Gowin IDE',
    content: `The Gowin EDA (also called Gowin IDE or GOWIN FPGA Designer) is the official development environment for Gowin FPGAs. It provides synthesis, place-and-route, timing analysis, and device programming in a single integrated tool.

## Downloading and Installing

1. Visit the Gowin Semiconductor website (www.gowinsemi.com) and navigate to the EDA tools download section.
2. Register for a free account. An education or standard edition license is free for most Gowin devices.
3. Download the appropriate version for your OS (Windows or Linux). The installer is typically around 500 MB-1 GB.
4. Install the IDE. On Windows, this is a standard installer. On Linux, extract the archive and run the setup script.
5. Request a license file. Gowin provides node-locked licenses based on your machine's MAC address. The license file is typically emailed within a few hours.
6. Place the license file in the location specified by the IDE (usually a path you configure in the IDE settings).

## Creating Your First Project

1. Launch Gowin IDE (FPGA Designer).
2. Select **File > New > FPGA Design Project**.
3. Enter a project name and location.
4. Select your target device. For example, for the Tang Nano 9K, choose:
   - Series: GW1NR
   - Device: GW1NR-9
   - Package: QFN88P (or the specific package for your board)
   - Speed: C6/I5
5. Click Finish. The project is created with a default file structure.

## Adding Design Files

1. Right-click on the Design hierarchy and select **Add Files**.
2. Add your Verilog (.v) or VHDL (.vhd) source files.
3. Set the top-level module by right-clicking the appropriate file.

**Example — LED blinker in Verilog:**

\`\`\`verilog
module top (
    input  wire clk,     // 27 MHz crystal on Tang Nano 9K
    output reg  [5:0] led
);

reg [23:0] counter;

always @(posedge clk) begin
    counter <= counter + 1;
    led <= counter[23:18];
end

endmodule
\`\`\`

## Adding Constraints

1. Create or add a physical constraints file (.cst) for pin assignments.
2. Create or add a timing constraints file (.sdc) for clock definitions.
3. The CST file maps your HDL port names to physical FPGA pins (see the Pin Constraints document for details).

## Running the Design Flow

The Gowin IDE design flow consists of:

1. **Synthesis:** Converts your HDL into a gate-level netlist. Click the Synthesis button or use Process > Synthesize. Check the synthesis report for warnings and resource utilization.

2. **Place & Route:** Maps the netlist onto the physical FPGA fabric. Click Place & Route. Review timing reports to ensure your design meets timing requirements.

3. **Generate Bitstream:** Creates the programming file (.fs for SRAM or .bin for flash). This file is used to program the FPGA.

## Programming the FPGA

1. Connect your board via USB (most Sipeed Tang boards have a built-in JTAG programmer).
2. Open the **Gowin Programmer** (bundled with the IDE).
3. Select your programming mode:
   - **SRAM Mode:** Programs the FPGA's volatile SRAM configuration. Fast but lost on power-off. Good for development.
   - **Flash Mode:** Programs the external SPI flash. Configuration persists across power cycles. Use for deployment.
4. Select the bitstream file and click Program.
5. The FPGA should now be running your design.

## Integrated Tools

The Gowin IDE also includes:
- **IP Core Generator (IPG):** Pre-built IP cores for common functions like PLLs, FIFOs, BSRAM controllers, MIPI interfaces, and more.
- **Floorplanner:** Visual tool for placing design elements on the FPGA fabric.
- **Timing Analyzer:** Review setup/hold times, critical paths, and clock domain crossings.
- **Logic Analyzer (GowinLAO):** An embedded logic analyzer similar to Xilinx ILA. Insert probe points in your design and capture signals in real-time via JTAG.`,
  },
  {
    title: 'Tang Nano Development Boards',
    content: `The Tang Nano series by Sipeed are popular, affordable development boards featuring Gowin FPGAs. They provide an excellent entry point for learning FPGA development.

## Tang Nano 9K

The most popular board in the series, featuring the GW1NR-9C FPGA with integrated 64 Mbit SDRAM.

**Specifications:**
- FPGA: GW1NR-LV9QN88PC6/I5 (8,640 LUT4s, 6,480 FFs)
- On-chip SDRAM: 64 Mbit (8 MB) — built into the FPGA package
- Block SRAM: 468 Kbit (26 x 18 Kbit blocks)
- PLLs: 2
- Flash: 32 Mbit external SPI flash for bitstream storage
- USB-C: Used for power, JTAG programming, and USB-to-UART
- HDMI connector: Direct HDMI output via LVDS I/O (no HDMI transmitter chip needed)
- User LEDs: 6
- User buttons: 2
- GPIO: Two rows of 2.54mm pin headers with ~40 I/O pins
- Clock: 27 MHz crystal oscillator
- Price: Approximately $15-20 USD

**Common uses:** Retro computer/console cores (NES, SNES, Game Boy), video processing, LED controllers, soft-core CPUs, learning FPGA development.

## Tang Nano 20K

A larger board featuring the GW2AR-18 FPGA with integrated SDRAM.

**Specifications:**
- FPGA: GW2AR-LV18QN88C8/I7 (20,736 LUT4s, 15,552 FFs)
- On-chip SDRAM: 64 Mbit (8 MB)
- Block SRAM: 828 Kbit (46 x 18 Kbit blocks)
- DSP blocks: 48 (18x18 multipliers)
- PLLs: 4
- Flash: 64 Mbit external SPI flash
- USB-C: Power, JTAG, UART
- HDMI connector
- MicroSD card slot
- User LEDs: 6
- GPIO: ~40 I/O pins
- Clock: 27 MHz crystal
- Price: Approximately $25-35 USD

The Tang Nano 20K has enough resources for more complex projects like complete retro computer implementations. The community has ported cores for Commodore 64, Atari 2600, and more.

## Board Setup

1. **Install USB drivers:** On Windows, you may need to install the FTDI driver or use Zadig to set the USB interface to WinUSB for JTAG access. On Linux, the drivers are typically built-in. On macOS, FTDI drivers may be needed.

2. **Connect the board:** Plug in via USB-C. The power LED should illuminate. On first power-up, the FPGA loads the factory demo from flash (typically an LED blink pattern).

3. **Verify JTAG connection:** Open Gowin Programmer. Click "Query/Detect Cable." Your board should appear as a USB device. Click "Scan Device" to detect the FPGA.

## Programming via Gowin Programmer

**SRAM programming (volatile):**
- Device > SRAM Program
- Select Operation: SRAM Program
- Browse to your .fs file
- Click Program — takes about 1-2 seconds
- Design runs immediately but is lost on power off

**Flash programming (persistent):**
- Device > SRAM Program
- Select Operation: exFlash Erase, Program
- Access Mode: External Flash Mode
- Browse to your .fs file
- Click Program — takes about 5-10 seconds
- Design persists across power cycles

## Using openFPGALoader (Alternative)

openFPGALoader is an open-source tool that can program Gowin FPGAs without the Gowin IDE:

\`\`\`bash
# Install (macOS)
brew install openfpgaloader

# Detect board
openFPGALoader --detect

# Program SRAM
openFPGALoader -b tangnano9k bitstream.fs

# Program flash
openFPGALoader -b tangnano9k -f bitstream.fs
\`\`\`

## HDMI Output

Both Tang Nano 9K and 20K feature HDMI connectors driven directly by the FPGA's LVDS I/O pins. This uses the FPGA to generate TMDS (Transition-Minimized Differential Signaling) encoding in logic. Community IP cores and Gowin's DVI TX IP can generate standard video signals. Typical supported resolutions: 640x480@60Hz, 720x480@60Hz, 800x600@60Hz, 1280x720@60Hz (20K only, timing-dependent).`,
  },
  {
    title: 'Gowin HDL Design',
    content: `This document covers Verilog design techniques and Gowin-specific considerations for HDL development on Gowin FPGAs.

## Verilog Basics for Gowin

Gowin's synthesis tool supports Verilog-2001 and a subset of SystemVerilog. VHDL is also supported. Most standard RTL coding styles synthesize correctly.

**Basic module structure:**

\`\`\`verilog
module my_design (
    input  wire        clk,      // System clock
    input  wire        rst_n,    // Active-low reset
    input  wire [7:0]  data_in,
    output reg  [7:0]  data_out,
    output wire        valid
);

// Internal signals
reg [7:0] data_reg;
wire      data_ready;

// Sequential logic
always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
        data_reg <= 8'b0;
        data_out <= 8'b0;
    end else begin
        data_reg <= data_in;
        data_out <= data_reg;
    end
end

// Combinational logic
assign valid = (data_out != 8'b0);
assign data_ready = valid;

endmodule
\`\`\`

## Gowin Primitives

Gowin provides technology-specific primitives that allow direct instantiation of hardware resources:

**OSER10 / IDES10:** 10:1 output serializer and 1:10 input deserializer. Essential for HDMI TMDS output — each TMDS lane requires serializing 10-bit symbols at pixel clock x10.

**TLVDS_OBUF:** True LVDS output buffer. Used for HDMI output on Tang Nano boards:
\`\`\`verilog
TLVDS_OBUF u_tmds_d0 (
    .O(tmds_d0_p),
    .OB(tmds_d0_n),
    .I(tmds_d0_serial)
);
\`\`\`

**CLKDIV:** Clock divider primitive. Divides a high-speed clock for use by parallel-side logic in SERDES interfaces.

**DPB / SPB / pROM:** Block RAM primitives. DPB (Dual-Port Block RAM), SPB (Single-Port Block RAM), and pROM (pseudo-ROM for initialized read-only memory). You can also infer these from behavioral Verilog:
\`\`\`verilog
reg [7:0] mem [0:1023];  // Infers BSRAM
initial $readmemh("init_data.hex", mem);  // Initialize from file

always @(posedge clk) begin
    if (we) mem[addr] <= data_in;
    data_out <= mem[addr];
end
\`\`\`

## PLLs (Phase-Locked Loops)

PLLs are critical for clock management. Use the Gowin IP Core Generator to configure PLLs:

1. In the IDE, go to Tools > IP Core Generator.
2. Select "PLL" under Clock.
3. Configure the input clock frequency (e.g., 27 MHz for Tang Nano boards).
4. Set desired output frequency. The tool calculates the PLL parameters (IDIV, FBDIV, ODIV).
5. Generate the Verilog wrapper.

Example PLL instantiation for generating a 74.25 MHz pixel clock from 27 MHz:
\`\`\`verilog
Gowin_rPLL u_pll (
    .clkout(pixel_clk),    // 74.25 MHz output
    .clkin(clk_27mhz),     // 27 MHz input
    .lock(pll_locked)       // PLL lock indicator
);
\`\`\`

Multiple clock outputs can be generated from a single PLL. The GW1N series has 2 PLLs; the GW2A series has 4.

## Finite State Machines

Gowin synthesis handles FSMs well with standard one-hot or binary encoding:
\`\`\`verilog
localparam S_IDLE = 2'd0;
localparam S_LOAD = 2'd1;
localparam S_RUN  = 2'd2;
localparam S_DONE = 2'd3;

reg [1:0] state, next_state;

always @(posedge clk or negedge rst_n) begin
    if (!rst_n) state <= S_IDLE;
    else        state <= next_state;
end

always @(*) begin
    next_state = state;
    case (state)
        S_IDLE: if (start)     next_state = S_LOAD;
        S_LOAD: if (loaded)    next_state = S_RUN;
        S_RUN:  if (finished)  next_state = S_DONE;
        S_DONE:                next_state = S_IDLE;
    endcase
end
\`\`\`

## Design Tips

- **Reset strategy:** Gowin FPGAs initialize all registers to 0 on configuration. You may not need explicit reset for all logic, but it's good practice for FSMs and control logic.
- **Clock domain crossings:** Use double-register synchronizers for signals crossing clock domains. Gowin provides no built-in CDC checking — use careful design practices.
- **Resource sharing:** The Gowin synthesis tool performs some automatic resource sharing, but manual optimization (e.g., sharing multipliers across time-multiplexed operations) can significantly reduce resource usage on smaller devices.
- **Simulation:** Use Icarus Verilog or ModelSim (Gowin bundles a limited ModelSim license) for testbench simulation before synthesis.`,
  },
  {
    title: 'Gowin FPGA Pin Constraints',
    content: `Pin constraint files define the mapping between your HDL design's ports and the physical pins on the FPGA. In the Gowin ecosystem, these are defined in CST (Constraint) files.

## CST File Format

The CST file uses a simple syntax for pin assignment and I/O configuration. Each line assigns a design port to a physical pin location and optionally specifies electrical characteristics.

**Basic syntax:**

\`\`\`
IO_LOC  "port_name"  pin_number;
IO_PORT "port_name"  IO_TYPE=io_standard;
\`\`\`

**Example for Tang Nano 9K:**

\`\`\`
// Clock input - 27 MHz oscillator
IO_LOC  "clk" 52;
IO_PORT "clk" IO_TYPE=LVCMOS33 PULL_MODE=NONE;

// Active-low reset button (directly active-low in button S1)
IO_LOC  "rst_n" 4;
IO_PORT "rst_n" IO_TYPE=LVCMOS33 PULL_MODE=UP;

// User LEDs (accent accent active low on Tang Nano 9K)
IO_LOC  "led[0]" 10;
IO_LOC  "led[1]" 11;
IO_LOC  "led[2]" 13;
IO_LOC  "led[3]" 14;
IO_LOC  "led[4]" 15;
IO_LOC  "led[5]" 16;
IO_PORT "led[0]" IO_TYPE=LVCMOS33 DRIVE=8;
IO_PORT "led[1]" IO_TYPE=LVCMOS33 DRIVE=8;
IO_PORT "led[2]" IO_TYPE=LVCMOS33 DRIVE=8;
IO_PORT "led[3]" IO_TYPE=LVCMOS33 DRIVE=8;
IO_PORT "led[4]" IO_TYPE=LVCMOS33 DRIVE=8;
IO_PORT "led[5]" IO_TYPE=LVCMOS33 DRIVE=8;

// HDMI output (directly active-low LVDS pairs)
IO_LOC  "tmds_clk_p"  69,68;
IO_LOC  "tmds_d0_p"   71,70;
IO_LOC  "tmds_d1_p"   73,72;
IO_LOC  "tmds_d2_p"   75,74;
IO_PORT "tmds_clk_p"  IO_TYPE=LVCMOS33D DRIVE=8;
IO_PORT "tmds_d0_p"   IO_TYPE=LVCMOS33D DRIVE=8;
IO_PORT "tmds_d1_p"   IO_TYPE=LVCMOS33D DRIVE=8;
IO_PORT "tmds_d2_p"   IO_TYPE=LVCMOS33D DRIVE=8;
\`\`\`

## IO_LOC Directive

\`IO_LOC\` assigns a design port to one or more physical pins.

- Single-ended: \`IO_LOC "signal" pin;\`
- Differential pairs: \`IO_LOC "signal_p" pos_pin,neg_pin;\` — the positive and negative pins are separated by a comma. For LVDS output, you only declare the positive signal in your HDL; the complementary pin is driven automatically.
- Bus signals: Use bracket notation: \`IO_LOC "data[0]" 25;\`

Pin numbers reference the FPGA's ball/pin numbers from the datasheet. For example, on the GW1NR-9 QFN88 package, pin 52 is the clock input on the Tang Nano 9K board.

## IO_PORT Directive

\`IO_PORT\` configures the electrical characteristics of a port.

**IO_TYPE values:**
- \`LVCMOS33\` — 3.3V LVCMOS (most common for Tang Nano boards)
- \`LVCMOS25\` — 2.5V LVCMOS
- \`LVCMOS18\` — 1.8V LVCMOS
- \`LVCMOS15\` — 1.5V LVCMOS
- \`LVCMOS33D\` — 3.3V LVCMOS differential (used for HDMI TMDS)
- \`LVDS25\` — 2.5V true LVDS (GW2A and above)
- \`SSTL25\` — Stub Series Terminated Logic for DDR2/DDR3 (GW2A)
- \`HSTL18\` — High-Speed Transceiver Logic

**PULL_MODE values:**
- \`NONE\` — No pull-up/down
- \`UP\` — Enable internal pull-up resistor (useful for buttons, active-low inputs)
- \`DOWN\` — Enable internal pull-down resistor
- \`KEEPER\` — Bus keeper (maintains last driven state)

**DRIVE values (for outputs):**
- \`4\`, \`8\`, \`12\`, \`16\`, \`24\` — Drive strength in mA. Default is 8 mA for LVCMOS33.

**SLEW_RATE values:**
- \`FAST\` — Fast slew rate (higher speed, more noise)
- \`SLOW\` — Slow slew rate (lower noise, reduced speed)

## SDC Timing Constraints

In addition to pin constraints (CST), timing constraints are specified in SDC (Synopsys Design Constraints) format:

\`\`\`
// Define the primary clock
create_clock -name clk -period 37.037 -waveform {0 18.518} [get_ports {clk}]

// Define a generated clock from PLL
create_clock -name pixel_clk -period 13.468 [get_nets {u_pll/clkout}]

// Set false path for asynchronous signals
set_false_path -from [get_ports {rst_n}]
\`\`\`

The period is specified in nanoseconds (37.037 ns = 27 MHz).

## Finding Pin Assignments

For Sipeed Tang Nano boards, pin assignments can be found in:
- The board schematic (available on Sipeed's GitHub repository)
- The board's pinout diagram
- Example projects provided by Sipeed`,
  },
  {
    title: 'Gowin FPGA Troubleshooting',
    content: `This guide covers common issues encountered when developing with Gowin FPGAs and their solutions.

## Synthesis Errors

**"Port X is not connected"**
This warning indicates an unused port in your design. If intentional (e.g., unused bits of a bus), you can safely ignore it. To suppress, explicitly leave the port unconnected: \`.unused_port()\` in your module instantiation.

**"Multiple drivers for net X"**
Two or more always blocks or assign statements are driving the same signal. In synthesizable Verilog, a net can only have one driver. Check for conflicting assignments and resolve by using a single always block or multiplexer.

**"Latch inferred for signal X"**
Occurs when a combinational always block does not assign a value to a signal in all code paths. This is usually unintentional and can cause design issues. Fix by ensuring all signals are assigned in every branch of if/case statements, or provide default assignments at the top of the always block:

\`\`\`verilog
always @(*) begin
    next_state = state;  // Default assignment prevents latch
    case (state)
        S_IDLE: if (start) next_state = S_RUN;
        S_RUN:  if (done)  next_state = S_IDLE;
    endcase
end
\`\`\`

**"Cannot find module X"**
The synthesis tool cannot locate an instantiated module. Ensure the source file containing that module is added to the project. Check for typos in the module name. If it's a Gowin primitive (like TLVDS_OBUF), ensure you're using the correct primitive name from the Gowin Primitives User Guide.

## Timing Issues

**Setup time violations:**
The design fails to meet timing on one or more paths. Check the timing report in the IDE (Process > Timing Analysis) to identify critical paths.

Solutions:
- Pipeline long combinational paths by inserting register stages
- Reduce the clock frequency
- Optimize logic (reduce fan-in/fan-out, simplify expressions)
- Use Gowin's physical constraints to guide placement of critical logic

**Hold time violations:**
Less common. Usually indicates clock skew issues. Gowin's place-and-route tool generally handles hold fixing automatically. If they persist, check your clock distribution network.

**Clock domain crossing issues:**
If signals cross between clock domains without proper synchronization, you may see intermittent failures that don't appear in timing reports. Always use double-register synchronizers or FIFOs for CDC.

## Programming Failures

**"Cannot detect device"**
- Check USB cable connection. Use a high-quality USB-C cable (some charge-only cables lack data lines).
- Install/reinstall USB drivers. On Windows, use Zadig to set the FTDI interface to WinUSB.
- On Linux, check udev rules. You may need to add a rule for the FTDI device and ensure your user is in the appropriate group (\`plugdev\` or similar).
- Try a different USB port. Avoid USB hubs if possible.
- Power cycle the board.

**"Program failed" or "Verify failed"**
- For SRAM programming: Ensure the bitstream matches the device. Check that the .fs file was generated for the correct device/package.
- For flash programming: The SPI flash may need to be erased first. Try "Erase" before "Program."
- Check the FPGA's JTAG chain. Use "Scan Device" to verify the FPGA is detected with the correct IDCODE.

**Board not running after flash programming:**
- Ensure you programmed the flash in the correct mode (External Flash Mode, not SRAM mode).
- Verify the bitstream file format. Use .fs for SRAM and flash programming via Gowin Programmer.
- Check the FPGA's boot mode pins. The Tang Nano boards have these set for SPI flash boot by default.

## Resource Utilization Issues

**Design too large for device:**
If synthesis or place-and-route reports that your design exceeds available resources:
- Check the utilization report for which resources are exhausted (LUTs, FFs, BSRAM, I/O).
- Optimize your design: share resources, reduce bit widths, use block RAM instead of distributed RAM.
- Consider a larger FPGA (e.g., move from Tang Nano 9K to Tang Nano 20K).

**BSRAM usage:**
Each Gowin BSRAM block is 18 Kbit. If you need a 1024x8 RAM, that's 8 Kbit — one BSRAM block. A 2048x16 RAM requires 32 Kbit — two blocks. Plan your memory usage to align with block boundaries to minimize waste.

## Simulation Issues

If your design works in simulation but not on hardware:
- Check for uninitialized signals. Simulation may default to X (unknown) while hardware defaults to 0.
- Check for timing violations (see above).
- Verify pin assignments match the physical board layout.
- Use the Gowin Logic Analyzer (GowinLAO) to capture internal signals on the running hardware for debugging.`,
  },
];

// ---------------------------------------------------------------------------
// Knowledge Set 3: Atari 8-bit Support
// ---------------------------------------------------------------------------

const atariDocs = [
  {
    title: 'Atari 8-bit Overview',
    content: `The Atari 8-bit computer family was produced from 1979 to 1992, spanning multiple models. Designed by Jay Miner (who later designed the Amiga), these machines featured an advanced custom chipset that made them standout gaming and multimedia platforms of the early home computer era.

## Models

**Atari 400 (1979):** The entry-level model. Membrane keyboard, single cartridge slot, 8 KB RAM (expandable to 48 KB). Originally intended as a game console with keyboard. Built-in RF output for TV connection.

**Atari 800 (1979):** The premium model. Full travel keyboard, two cartridge slots, expandable to 48 KB RAM via internal RAM board slots. Also featured well-shielded internal construction in a heavy-duty case with an RF modulator.

**Atari 1200XL (1983):** Redesigned case with a sleeker look, built-in 64 KB RAM, and function keys (F1-F4, Help). However, it had compatibility issues with some software and peripherals due to revised OS ROM and removed some PBI features. Relatively rare today.

**Atari 600XL (1983):** Compact, cost-reduced model with 16 KB RAM. Features the Parallel Bus Interface (PBI) expansion connector. Very small footprint but limited by its low base RAM.

**Atari 800XL (1983):** The most popular Atari 8-bit model. 64 KB RAM, full keyboard, built-in BASIC, PBI connector, and excellent software compatibility. If you're starting with Atari 8-bit computers, this is the model to get.

**Atari 65XE (1985):** Redesigned XL series in the XE case style (matching the Atari ST aesthetic). 64 KB RAM. Functionally similar to the 800XL but in a different case.

**Atari 130XE (1985):** 128 KB RAM (64 KB base + 64 KB bank-switched extended memory). The extended memory can be used by compatible software for data storage or as a RAM disk. The most capable stock Atari 8-bit.

**Atari XEGS (1987):** A game-console version of the 65XE. Detachable keyboard, built-in game (Missile Command or Bug Hunt), cartridge slot. Fully compatible with the 8-bit software library.

## Custom Chipset

The Atari 8-bit's power came from its three custom chips, collectively known as the "Atari custom chipset":

**ANTIC (Alphanumeric Television Interface Controller):** A dedicated display processor with its own instruction set ("display list"). ANTIC fetches a program (the display list) from RAM and executes it to generate the display. This allows different graphics modes to be mixed on the same screen — for example, a text status line above a graphics playfield. ANTIC supports 14 graphics modes with varying resolutions and color depths, from 40x24 text to 320x192 graphics.

**GTIA (Graphic Television Interface Adapter):** Works alongside ANTIC to produce the final video output. GTIA handles color generation, player-missile (sprite) graphics, and collision detection. It adds three additional color interpretation modes to ANTIC's base modes:
- Mode 9: 16 shades of one hue
- Mode 10: 9 different hues (useful for colorful static screens)
- Mode 11: 16 hues at one luminance

GTIA also manages four "players" (8 pixels wide) and four "missiles" (2 pixels wide) — the Atari's hardware sprites. Players can be combined and prioritized in flexible ways.

**POKEY (POtentiometer and KEYboard):** Handles audio output (four independent channels), keyboard scanning, serial I/O (the SIO bus), random number generation, and paddle/potentiometer input. Each audio channel can produce square waves with variable frequency (8-bit frequency dividers) and either pure tone or polynomial-based noise. POKEY's audio capabilities made the Atari excellent for music and sound effects.

## CPU

All Atari 8-bit models use the MOS 6502C (called "Sally") CPU running at 1.79 MHz (NTSC) or 1.77 MHz (PAL). Sally is a standard 6502 with an additional HALT line that allows ANTIC to steal CPU cycles for DMA (Direct Memory Access) — this is how ANTIC reads the display list and screen data without conflicting with the CPU.`,
  },
  {
    title: 'Atari Hardware Setup',
    content: `Setting up an Atari 8-bit computer with modern equipment requires some adapter solutions. This guide covers common connection scenarios.

## Video Output to Modern TVs

**RF output (all models):** The built-in RF modulator outputs an analog TV signal on channel 2 or 3. Modern TVs with a coaxial antenna input can sometimes receive this signal, but quality is poor (fuzzy, noisy). Not recommended for regular use.

**Composite video:** The Atari 800XL, 130XE, and 65XE have a 5-pin DIN monitor output that provides composite video and mono audio. You'll need a 5-pin DIN to RCA cable. Connect the composite video (yellow RCA) to your TV or a composite-to-HDMI adapter. This gives a much better picture than RF.

**S-Video modification:** For the best picture quality from original hardware, an S-Video modification is highly recommended. Kits are available that separate the chroma (color) and luma (brightness) signals. S-Video produces a much sharper image with less color bleeding. UAV (Ultimate Atari Video) by Bryan is the gold-standard video upgrade, providing clean S-Video and composite output from a small plug-in board.

**HDMI solutions:** The Atari 8-bit community has produced HDMI adapter solutions. The SOPHIA 2 is a direct replacement for the GTIA chip that outputs a digital DVI/HDMI signal directly from the Atari. It produces a pixel-perfect, clean HDMI output and is the best option for modern displays.

## SIO (Serial I/O) Bus

The Atari SIO port is the primary peripheral interface. It's a proprietary 13-pin connector used for daisy-chaining peripherals: disk drives, printers, cassette interface, and modems.

**SIO pinout key signals:**
- Pin 1: Clock input
- Pin 3: Data input
- Pin 5: Data output
- Pin 7: Command
- Pin 10: +5V/Ready
- Pin 13: Ground (active low interrupt)

SIO operates as a serial bus at speeds from 600 baud (cassette) to 19,200 baud (standard disk) to 68,000+ baud (with high-speed SIO modifications like the Happy drive upgrade or modern devices like SIO2SD).

**SIO peripherals:**
- **Atari 810/1050 disk drives:** The standard 5.25" floppy drives. The 810 reads single density (90 KB); the 1050 reads enhanced density (130 KB) and single density.
- **Atari 850 Interface Module:** Provides RS-232 serial ports and a Centronics parallel port for printers and modems.
- **Atari 410/1010 Program Recorder:** Cassette data storage. Very slow but very cheap storage media.

## Joystick Ports

Atari 8-bit computers use standard DE-9 (9-pin) Atari-style joystick ports. The 400/800 have four ports; the XL/XE models have two.

Compatible controllers:
- Standard Atari 2600 joysticks (CX-40 and compatibles)
- Atari paddles (via paddle port — uses POKEY's potentiometer inputs)
- Atari trakball (CX-22/CX-80)
- Modern USB-to-Atari adapters allow using modern gamepads

## Power Supply

**Atari 400/800:** Use a specific 10.5V AC power supply (Atari part CO61982). These are proprietary and not interchangeable with other systems.

**Atari XL/XE models:** Use a 5V DC power supply with a barrel connector. The stock Atari supply is a linear "brick" type. Modern replacements should provide 5V DC at 1.5-2A minimum. Be very careful about polarity: the Atari XL/XE uses **center pin positive**. Using a wrong-polarity supply will damage the computer.

**Recommendation:** Replace old power supplies with modern switching supplies from reputable Atari hardware vendors. The old linear supplies can develop faults and output incorrect voltage, potentially damaging the computer.

## Cartridge Slot

The cartridge slot accepts Atari 8-bit game and software cartridges. It's also the basis for modern multi-cart solutions:
- **The!Cart:** A programmable multi-cart that can hold hundreds of cartridge images on an SD card.
- **Ultimate Cart:** Similar concept with a large flash memory for cartridge ROMs loaded from SD card.
- Standard cartridges range from 8 KB to 128 KB+ using bank switching.`,
  },
  {
    title: 'Atari Storage Solutions',
    content: `Modern storage solutions have transformed the Atari 8-bit experience, replacing slow and unreliable floppy drives with fast, reliable solid-state alternatives.

## SIO2SD

SIO2SD is one of the earliest and most popular modern storage solutions. It connects to the Atari's SIO port and emulates one or more Atari disk drives using an SD card.

**Features:**
- Emulates up to 4 Atari disk drives (D1: through D4:)
- Reads ATR disk image files from SD card (FAT16/FAT32)
- Supports single density (90 KB), enhanced density (130 KB), and double density (180 KB) images
- Small LCD display for file browsing and image selection
- Buttons for navigation
- Supports high-speed SIO for faster loading
- Powered by the SIO port's +5V line

**Usage:** Format an SD card as FAT32, copy your ATR disk image files to it, insert into SIO2SD, connect to the Atari's SIO port, and power on. Use the buttons and LCD to select which disk image is mounted as each drive number.

## FujiNet

FujiNet is the most advanced modern Atari peripheral. It connects to the SIO port and provides disk emulation, networking, and more via WiFi.

**Features:**
- SIO disk drive emulation (reads ATR images from SD card or network)
- Built-in WiFi for internet connectivity
- TNFS (Trivial Network File System) client — mount disk images from network servers
- Modem emulation — use BBS software over WiFi with actual internet connectivity
- Printer emulation — capture Atari print output as PDF or PNG files
- Network adapter — Atari programs can make TCP/UDP connections via FujiNet's N: device
- Firmware updates over WiFi
- Web-based configuration interface

**Setup:**
1. Connect FujiNet to the Atari's SIO port.
2. Power on. FujiNet creates a WiFi access point for initial configuration.
3. Connect a phone or computer to the FujiNet AP and open the web configuration page.
4. Configure your home WiFi network credentials.
5. Once connected, use the Atari-side CONFIG program (boots from FujiNet) to mount disk images, configure TNFS servers, etc.

FujiNet has an active development community and receives regular firmware updates adding new features. It's the single most recommended modern Atari peripheral.

## SDrive-MAX

SDrive-MAX is an Arduino-based SIO disk emulator with a color touchscreen display.

**Features:**
- Color TFT touchscreen for easy file browsing
- Emulates up to 4 disk drives
- Supports ATR, XEX (direct executable loading), and CAS (cassette) images
- Touchscreen interface for drive assignments
- Can be built from readily available components (Arduino Mega, TFT shield, SD module)

SDrive-MAX is open-source hardware and software. Pre-built units are available from various Atari community vendors.

## ATR Disk Image Format

ATR is the standard Atari disk image format. It's a simple binary format with a 16-byte header followed by raw sector data.

**ATR header structure:**
- Bytes 0-1: Magic number (0x96, 0x02)
- Bytes 2-3: Disk image size in paragraphs (16-byte units), low word
- Bytes 4-5: Sector size (128 for SD/ED, 256 for DD)
- Bytes 6-7: High word of size in paragraphs
- Bytes 8-15: Reserved/flags

**Common ATR sizes:**
- Single Density (SD): 720 sectors x 128 bytes = 92,160 bytes + 16 byte header
- Enhanced Density (ED): 1,040 sectors x 128 bytes = 133,120 bytes + 16 byte header
- Double Density (DD): 720 sectors x 256 bytes = 184,320 bytes + 16 byte header (first 3 sectors are still 128 bytes)

**XEX format:** A simpler format for loading executable programs directly. XEX files contain load address headers followed by binary data. Many games and demos are distributed as XEX files for convenience.

## Other Solutions

**DriveWire:** Uses a serial/USB connection to a PC. The PC software serves disk images to the Atari. More complex setup but very flexible.

**Atari floppy drive replacements:** Gotek floppy emulators can be installed inside Atari 810/1050 drive cases, reading disk images from USB flash drives. This is a good option if you want to maintain the original drive aesthetic.

**Hard drive emulation:** The APE (Atari Peripheral Emulator) software on PC can emulate not just disk drives but also printers, modems, and other SIO peripherals over a standard SIO-to-USB cable.`,
  },
  {
    title: 'Atari BASIC Programming',
    content: `Atari BASIC is the built-in programming language on Atari XL/XE computers (built into ROM, enabled by default on boot). While not the fastest BASIC, it provides direct access to the Atari's graphics and sound hardware.

## Getting Started

On the 800XL, 65XE, or 130XE, Atari BASIC starts automatically if no cartridge is inserted and no disk is booted. You'll see the "READY" prompt with a cursor.

Basic commands:
\`\`\`
10 PRINT "HELLO WORLD"
20 GOTO 10
RUN
\`\`\`

Press Break to stop a running program. Use LIST to display program lines, and NEW to clear the current program.

## Key Differences from Other BASICs

Atari BASIC has some unique characteristics:
- Line numbers are mandatory (1-32767)
- Variables are limited to a token table (about 128 variables)
- String handling uses DIM to allocate string space: \`DIM A$(20)\` allocates a 20-character string
- String arrays are not supported — use substring notation: \`A$(5,10)\` extracts characters 5-10
- Programs are tokenized immediately on entry, making them compact in memory

## Graphics Modes

Atari BASIC provides the GRAPHICS command to set screen modes:

\`\`\`
GRAPHICS 0  : REM 40x24 text mode, 1 color + background
GRAPHICS 7  : REM 160x96 pixels, 4 colors
GRAPHICS 8  : REM 320x192 pixels, 1.5 colors (luminance only)
GRAPHICS 15 : REM 160x192 pixels, 4 colors (no text window)
\`\`\`

Adding 16 to the mode number removes the text window at the bottom (full-screen graphics):
\`\`\`
GRAPHICS 8+16  : REM Full-screen 320x192
\`\`\`

**Drawing commands:**
\`\`\`
COLOR 1                    : REM Set drawing color register
PLOT 10,10                 : REM Plot a single pixel
DRAWTO 100,80              : REM Draw line from last PLOT to this point
SETCOLOR 0,8,6             : REM Set color register 0: hue=8 (blue), luminance=6
\`\`\`

The Atari has 5 color registers (0-4). SETCOLOR takes register number, hue (0-15), and luminance (0-14, even numbers only).

## Sound

POKEY sound is accessed through the SOUND command:

\`\`\`
SOUND voice, frequency, distortion, volume
\`\`\`

- Voice: 0-3 (four independent channels)
- Frequency: 0-255 (lower value = higher pitch)
- Distortion: 0, 2, 4, 6, 8, 10, 12, 14 (10 = pure tone, others add noise)
- Volume: 0-15

\`\`\`
SOUND 0,100,10,8     : REM Pure tone on channel 0
SOUND 1,50,10,8      : REM Higher pure tone on channel 1
SOUND 0,0,0,0        : REM Silence channel 0
\`\`\`

## Player-Missile Graphics (Sprites)

Atari BASIC can set up player-missile graphics through POKE commands to hardware registers:

\`\`\`
10 POKE 559,46            : REM Enable PM graphics (DMACTL)
20 POKE 53277,3           : REM Enable players and missiles (GRACTL)
30 PMBASE=PEEK(106)-16    : REM Calculate PM base address
40 POKE 54279,PMBASE      : REM Set PMBASE
50 A=PMBASE*256+1024      : REM Player 0 data starts here
60 FOR I=A TO A+7
70 POKE I,255             : REM Write 8 lines of solid player data
80 NEXT I
90 POKE 53248,100         : REM Set Player 0 horizontal position (HPOSP0)
100 POKE 704,88           : REM Set Player 0 color (COLPM0)
\`\`\`

## Turbo BASIC XL

Turbo BASIC XL is an enhanced, free BASIC that is fully compatible with Atari BASIC but adds:
- Structured programming: DO/LOOP, WHILE/WEND, REPEAT/UNTIL, IF/ELSE/ENDIF, PROC/ENDPROC
- Faster execution (2-3x faster than Atari BASIC)
- Additional commands: CIRCLE, FILLTO, BLOAD, BSAVE, TIME, %PUT, %GET
- Inline machine language with DPOKE and USR improvements

Turbo BASIC XL is highly recommended as a replacement for stock Atari BASIC.

## Memory Map Essentials

Key memory addresses for Atari programmers:
- $D000-$D0FF: GTIA registers (graphics/color)
- $D200-$D2FF: POKEY registers (sound/keyboard/serial)
- $D300-$D3FF: PIA registers (port control)
- $D400-$D4FF: ANTIC registers (display list/scrolling)
- $0230: SDLSTL/H — Display list pointer
- $02C4-$02C8: Color shadow registers (COLPF0-COLBK)
- $02F0: Screen memory pointer (SAVMSC)`,
  },
  {
    title: 'Atari Software and Games',
    content: `The Atari 8-bit platform has a rich library of software spanning games, productivity, and creative tools. This guide covers essential titles and notable software.

## Landmark Games

**Star Raiders (1979):** Often cited as one of the most important video games ever made. A first-person space combat simulation with a galactic map, hyperspace travel, energy management, and shield systems. Star Raiders demonstrated that home computers could deliver arcade-quality experiences and pushed the Atari hardware to its limits. Designed by Doug Neubauer, who also designed the POKEY chip.

**M.U.L.E. (1983):** A multiplayer economic strategy game by Dan Bunten (Ozark Softscape) and published by Electronic Arts. Up to four players compete to colonize a planet by acquiring land, producing resources (food, energy, smithore, crystite), and trading in a real-time auction. M.U.L.E. is consistently ranked among the greatest games ever made and perfectly leverages the Atari's four joystick ports.

**Rescue on Fractalus! (1984):** A first-person flying and rescue game by Lucasfilm Games. Famous for its fractal-generated mountain terrain — revolutionary for 1984. Players fly through alien canyons to rescue downed pilots while being attacked by enemy saucers and gun emplacements.

**Alternate Reality: The City (1985):** An ambitious first-person RPG with smooth scrolling, weather effects, day/night cycles, and procedurally generated encounters. Featured remarkable music by Gary Gilbertson.

**Archon (1983):** A chess-like strategy game where capturing a piece triggers a real-time action combat sequence. Players control Light or Dark forces, each with unique units (Wizard, Banshee, Golem, Phoenix, etc.).

## Productivity Software

**AtariWriter / AtariWriter Plus:** Atari's official word processor. AtariWriter Plus was a capable document editor with mail merge, headers/footers, and printer formatting. Widely used in schools.

**VisiCalc:** The first electronic spreadsheet, available for the Atari 800. While the Apple II version is more famous, the Atari version was fully functional.

**Print Shop:** The popular Broderbund publishing tool for creating banners, greeting cards, signs, and letterheads with custom graphics.

**SynCalc / SynFile / SynTrend:** A suite of productivity applications (spreadsheet, database, data analysis) by Synapse Software. Professional-grade tools for the Atari platform.

## Creative Tools

**Atari Music Composer:** Early music composition software using standard music notation. Output through POKEY's four channels.

**Music Construction Set (EA):** A more advanced music tool by Will Harvey, allowing composition by dragging notes onto a staff.

**The Graphics Magician:** A game-development graphics tool for creating and animating vector and bitmap graphics.

**Atari Logo:** A Logo programming language implementation featuring turtle graphics. Excellent for learning programming concepts.

## The Demo Scene

The Atari 8-bit has an active demo scene that continues to produce stunning visual and audio productions that push the hardware beyond what was thought possible:

- **Numen (Taquart, 2009):** A landmark Atari 8-bit demo featuring 3D-rendered scenes, smooth animations, and advanced POKEY music. Won multiple demo party competitions.
- **Bitter Reality (Taquart):** An early influential Atari 8-bit demo.
- **Various by Agenda, Slight, Lamers:** Active demo groups producing new Atari 8-bit content.

Modern Atari demo techniques include: DLI (Display List Interrupt) color tricks, VBXE (Video Board XE) enhanced graphics, software sprites beyond hardware limits, advanced POKEY music players (RMT, LZSS), and cycle-exact raster effects.

## Essential Utilities

**SpartaDOS X:** The most advanced DOS for the Atari 8-bit. A command-line operating system with subdirectories, batch files, device drivers, and timestamps. Runs from a cartridge with a banked ROM design.

**MyDOS:** A popular and reliable DOS compatible with Atari DOS 2.5. Supports double density and larger disks.

**BobTerm:** A terminal emulator for modem/BBS access. Now used with FujiNet for retro BBS access over WiFi.

**MADS (Mad Assembler):** A modern cross-assembler for 6502 code targeting the Atari 8-bit. Runs on PC, supports macros, local labels, and Atari-specific features.

**Altirra:** While not Atari software itself, Altirra is the premier Atari 8-bit emulator (Windows). It provides cycle-accurate emulation, excellent debugging tools, and supports virtually all Atari hardware and peripherals.`,
  },
  {
    title: 'Atari Hardware Repairs',
    content: `As Atari 8-bit computers age, certain hardware failures become increasingly common. This guide covers the most frequent repair needs and their solutions.

## Power Supply Replacement

Original Atari power supplies are a major failure risk. The XL/XE models use a 5V DC linear supply that can develop faults with age, potentially outputting dangerously high voltage.

**Symptoms of power supply failure:** Computer won't power on, intermittent crashes, screen artifacts, or outright component damage.

**Recommendation:** Replace the original supply with a modern regulated switching supply. Several Atari community vendors sell purpose-built replacements:
- Ensure the replacement outputs 5V DC at a minimum of 1.5A (2A preferred)
- Verify **center-positive** polarity (this is critical — center-negative will damage the computer)
- Measure the output voltage with a multimeter before connecting to the Atari

For the Atari 400/800, the power supply is 10.5V AC. Replacements are available but rarer — check Atari-specific hardware vendors.

## Keyboard Membrane Repair

XL/XE models use a Mylar membrane keyboard with carbon traces. Over time, the membrane's conductive traces crack or the contact points wear out.

**Symptoms:** Keys that don't respond, intermittent key presses, multiple keys failing (often in a row or column pattern).

**Diagnosis:** Open the computer and carefully inspect the membrane. Look for cracks in the carbon traces, especially at fold points. You can test continuity with a multimeter.

**Solutions:**
- **Conductive paint/ink:** Small trace breaks can be repaired with conductive silver paint. Clean the break area and carefully bridge the gap.
- **Replacement membrane:** New reproduction membranes are manufactured by community members (notably "Best Electronics" and other vendors). A new membrane completely restores keyboard function.
- **Mechanical keyboard mod:** Some enthusiasts replace the membrane with a mechanical keyboard adapter using Cherry MX or similar switches. This is a more involved modification but provides a much better typing experience.

## FREDDIE Chip Issues (130XE)

The FREDDIE chip (CO61991) is a memory management IC found in the 130XE (and some late 800XL revisions). It handles address multiplexing for the DRAM chips and manages the 130XE's bank-switched extended memory.

**Symptoms of FREDDIE failure:** Black screen on power-up, RAM test failures, extended memory not working, random crashes.

**FREDDIE is known to fail** more often than other chips in the system. Replacement FREDDIE chips are becoming scarce, but FPGA-based replacements are now available from the Atari community. These drop-in replacements are soldered or socketed in place of the original FREDDIE.

## RAM Chip Failures

Atari XL/XE computers use 4164 (64K x 1-bit) or 41256 (256K x 1-bit) DRAM chips. There are typically 8 chips providing 64 KB (or 16 chips for 128 KB in the 130XE).

**Symptoms:** Garbled screen on boot, Atari fails memory self-test (colored screen with error pattern), random crashes, inability to boot.

**Diagnosis:** The Atari's built-in self-test (hold Option while powering on) includes a memory test. A specific color pattern on screen indicates which RAM chip has failed:
- The screen is divided into sections, each representing a RAM IC
- A red/bright section indicates a failing chip
- Cross-reference the section position with the motherboard RAM layout

**Repair:** Desolder the failed chip and replace with a known-good 4164 or 41256. If chips are soldered directly (not socketed), consider installing a socket first for future serviceability.

## ANTIC / GTIA / POKEY Failures

These custom chips are essential and irreplaceable from new production (though some FPGA replacements exist):

**ANTIC failure:** No display or corrupted display. Since ANTIC generates the display through its display list processor, failure typically means no usable video output.

**GTIA failure:** No color, garbled colors, or no video output. The SOPHIA/SOPHIA2 FPGA replacement for GTIA not only fixes a dead GTIA but upgrades the video output to HDMI.

**POKEY failure:** No sound, no keyboard input (POKEY handles keyboard scanning), corrupted audio. POKEY is also responsible for SIO communication, so a failing POKEY may prevent disk drives from working. The PokeyONE and PokeyMAX are FPGA replacements that provide enhanced audio capabilities.

## Voltage Regulator

The internal 5V voltage regulator (typically a 7805 or equivalent) can overheat and fail, especially in poorly ventilated setups. Adding a small heatsink to the voltage regulator is a good preventive measure. Modern drop-in replacements (like Murata switching regulators) run cooler and more efficiently.

## General Maintenance Tips

- **Cleaning:** Use isopropyl alcohol and cotton swabs to clean card edge connectors, cartridge slots, and SIO ports. Oxidation on these contacts is a common cause of intermittent connections.
- **Capacitors:** While not as universally problematic as on Amigas, older Atari machines may benefit from a capacitor replacement, especially if exhibiting audio hum or video instability.
- **Chip reseat:** If your Atari uses socketed chips (common in the 400/800), remove and reseat all ICs to address oxidized socket contacts.`,
  },
];

// ---------------------------------------------------------------------------
// Knowledge Set 4: Geek Help
// ---------------------------------------------------------------------------

const geekDocs = [
  {
    title: 'Welcome to Geek Help',
    content: `Welcome to Geek Help, your friendly support resource for retro computing, FPGA development, and all things tech-geeky. Whether you're a seasoned hardware hacker or just getting started with your first vintage computer, we're here to help.

## What We Cover

**Amiga Computers:** We provide comprehensive support for the Commodore Amiga family. Whether you're trying to get your A500 running again, set up networking on your A1200, configure WinUAE emulation, or install a modern accelerator like the Vampire or PiStorm, we can help. Our Amiga knowledge base covers hardware troubleshooting, AmigaOS installation, memory upgrades, the custom chipset (OCS/ECS/AGA), audio and graphics, emulation, networking, and popular software.

**Gowin FPGAs:** We support developers working with Gowin Semiconductor's FPGA products, including the popular Sipeed Tang Nano development boards (Tang Nano 9K, Tang Nano 20K). Our coverage includes the Gowin IDE setup, Verilog design for Gowin devices, pin constraint files, PLL configuration, and troubleshooting common synthesis and programming issues.

**Atari 8-bit Computers:** We cover the classic Atari 8-bit line from the original Atari 400/800 through the XL and XE series. Topics include hardware setup with modern displays, SIO peripherals, modern storage solutions (SIO2SD, FujiNet, SDrive-MAX), Atari BASIC programming, the custom chipset (ANTIC, GTIA, POKEY), hardware repairs, and the vibrant Atari software library.

## How to Get Help

When asking a question, providing specific details helps us give you the best answer:

- **For hardware issues:** Tell us the exact model, any modifications installed, symptoms (visual, audio, behavioral), and what you've already tried.
- **For software issues:** Include the operating system version, the software you're using, and any error messages.
- **For FPGA questions:** Share your target device, board, IDE version, and relevant code snippets or error messages.
- **For emulation:** Specify the emulator name and version, host operating system, and what you're trying to achieve.

## Community Resources

Beyond our support system, these communities are excellent resources:

- **Amiga:** English Amiga Board (EAB) forums, Amiga.org, Lemon Amiga
- **Atari:** AtariAge forums, Atari 8-bit FAQ, ABBUC (Atari Bit Byter User Club)
- **Gowin FPGA:** Sipeed forums, FPGA subreddit, Gowin official forums
- **General retro:** Vogons forums, Retro Computing Stack Exchange, various Discord servers and subreddits

We're passionate about these platforms and genuinely enjoy helping people discover (or rediscover) these amazing machines. Don't hesitate to ask — no question is too basic or too obscure!`,
  },
  {
    title: 'Retro Computing Basics',
    content: `Retro computing is the hobby of collecting, restoring, using, and programming vintage computers. For many enthusiasts, it's a way to reconnect with the machines that inspired their love of technology. For newcomers, it's a fascinating window into computing history.

## What Counts as "Retro"?

While the definition is debated, retro computing generally covers computers from the late 1970s through the mid-1990s — the era of 8-bit and 16/32-bit home computers before the IBM PC clone dominated the market. Key platforms include:

**8-bit era (late 1970s-mid 1980s):**
- Apple II (1977) — The machine that launched the personal computer revolution
- Atari 400/800 (1979) — Advanced graphics and sound for its time
- Commodore 64 (1982) — The best-selling single computer model ever (approximately 17 million units)
- ZX Spectrum (1982) — Hugely popular in the UK and Europe
- MSX (1983) — A standardized home computer architecture popular in Japan and Europe
- Amstrad CPC (1984) — Popular in Europe, integrated monitor design

**16/32-bit era (mid 1980s-mid 1990s):**
- Commodore Amiga (1985) — Multimedia powerhouse, advanced custom chipset
- Atari ST (1985) — Popular for MIDI music production, strong in Europe
- Apple Macintosh (1984) — Pioneered the mainstream GUI
- Acorn Archimedes (1987) — Used the first ARM processors

## Why Retro Computing?

People are drawn to retro computing for many reasons:

- **Simplicity:** These machines are understandable at a fundamental level. You can learn how every chip works and what every byte of memory does. Modern computers are incomprehensibly complex by comparison.
- **Creativity within constraints:** Working within tight memory and processing limits inspires creative solutions. Writing efficient code for a 1 MHz 6502 with 64 KB of RAM is a deeply satisfying challenge.
- **Nostalgia:** Many enthusiasts grew up with these machines and enjoy revisiting them.
- **Historical appreciation:** These platforms pioneered concepts we take for granted — GUIs, multitasking, digital audio, computer graphics.
- **Community:** Retro computing has a vibrant, welcoming global community.

## Where to Find Hardware

- **Online marketplaces:** eBay, Amibay (Amiga-specific), AtariAge marketplace
- **Retro computing shows:** Events like VCF (Vintage Computer Festival), Amiga shows, and local retro computing meetups
- **Thrift stores and estate sales:** Occasionally yield finds
- **Community forums:** Members often sell or trade equipment

## Essential Tools for the Hobby

- **Multimeter:** For checking voltages, continuity, and diagnosing hardware issues
- **Soldering iron:** A temperature-controlled station (like Hakko FX-888D) for repairs and modifications
- **Logic probe or oscilloscope:** For advanced hardware debugging
- **Isopropyl alcohol (90%+):** For cleaning boards, contacts, and connectors
- **Anti-static precautions:** ESD wrist strap and anti-static mat
- **Floppy disk imaging tools:** Kryoflux, Greaseweazle, or SuperCard Pro for reading and writing legacy floppy disk formats
- **USB-to-serial adapters:** For communicating with vintage machines
- **EPROM programmer:** For burning ROM chips (TL866II Plus is a popular, affordable option)

## Getting Started

If you're new to retro computing, here's a practical path:
1. Pick a platform that interests you (or one you have nostalgia for)
2. Acquire the hardware — start with common, affordable models
3. Get a modern storage solution (SD-card-based device) to avoid floppy disk headaches
4. Join the community forums for your platform
5. Try some classic software and games
6. Start a simple programming project in the machine's native language
7. Consider emulation alongside real hardware — it's a great way to learn and develop software`,
  },
  {
    title: 'FPGA Development Introduction',
    content: `FPGAs (Field-Programmable Gate Arrays) are integrated circuits that can be configured by the user to implement custom digital logic circuits. Unlike microprocessors that execute software instructions sequentially, FPGAs implement hardware logic that operates in parallel. This makes them incredibly powerful for certain applications.

## What Is an FPGA?

An FPGA consists of:
- **Logic blocks (CLBs/LUTs):** Configurable look-up tables that can implement any boolean function. A 4-input LUT can represent any function of 4 boolean variables.
- **Flip-flops/Registers:** Storage elements for sequential logic.
- **Routing fabric:** A programmable interconnect network connecting logic blocks.
- **I/O blocks:** Configurable input/output pins supporting various electrical standards.
- **Specialized blocks:** Block RAM, DSP (multiplier) blocks, PLLs (clock management), and sometimes hard processor cores.

When you "program" an FPGA, you're configuring these elements and their interconnections to implement your desired digital circuit. The FPGA literally becomes the hardware you described.

## Why Use FPGAs?

**Parallel processing:** FPGAs can execute many operations simultaneously. A processor handles instructions one at a time (or a few with pipelining); an FPGA implements all logic simultaneously.

**Deterministic timing:** Hardware logic has predictable, consistent timing. Critical for applications like video processing, communications, and real-time control.

**Retro computing:** FPGAs are widely used to recreate vintage computer and game console hardware. Projects like MiSTer FPGA implement complete systems (Amiga, Atari, NES, SNES, Genesis, etc.) in an FPGA with cycle-accurate behavior.

**Prototyping:** FPGA designs can be tested and modified without manufacturing new silicon. This makes FPGAs essential for ASIC prototyping.

**Education:** FPGAs are an excellent way to learn digital design, computer architecture, and hardware-software interaction.

## Popular Development Boards

**Sipeed Tang Nano 9K / 20K:** Affordable Gowin FPGA boards ($15-35). Excellent for beginners. Features HDMI output, LEDs, buttons, and GPIO. Supported by free Gowin EDA tools and the open-source Yosys/nextpnr toolchain.

**Lattice iCEBreaker:** Uses the Lattice iCE40 UP5K FPGA. Fully supported by the open-source IceStorm toolchain (Yosys + nextpnr + icepack). Very popular in the open-source FPGA community.

**Digilent Basys 3 / Nexys A7:** Xilinx Artix-7 based boards widely used in university courses. Good documentation and tutorials. Uses the Xilinx Vivado toolchain (free for these devices).

**Terasic DE10-Nano:** Intel (Altera) Cyclone V FPGA with ARM hard processor core. The platform for the MiSTer FPGA retro gaming project. Features HDMI output, USB, and SDRAM.

**Digilent Arty A7:** Xilinx Artix-7 board targeting embedded/IoT applications. Supports MicroBlaze soft processor and Xilinx Vivado/Vitis.

## Getting Started with FPGA Development

**1. Choose your tools and board.** For beginners on a budget, the Tang Nano 9K with Gowin EDA is hard to beat. For open-source purists, a Lattice iCE40 board with the IceStorm toolchain is excellent.

**2. Learn an HDL (Hardware Description Language).** Verilog and VHDL are the two primary HDLs:
- **Verilog** has a C-like syntax and is more popular in North America and Asia. Good for beginners.
- **VHDL** has an Ada-like syntax and is more popular in Europe. More verbose but strongly typed.
- **SystemVerilog** extends Verilog with verification features and modern syntax.

**3. Start with simple projects:**
- LED blinker (the "Hello World" of FPGAs)
- Button debouncing
- Seven-segment display driver
- UART transmitter/receiver
- VGA/HDMI signal generator

**4. Learn key concepts:**
- **Combinational vs. sequential logic:** Combinational logic computes outputs directly from inputs (like an adder). Sequential logic includes memory elements (flip-flops) and operates on clock edges.
- **Clocking:** Everything in synchronous FPGA design is driven by clock signals. Understanding clock domains, setup/hold times, and timing constraints is essential.
- **State machines:** Finite state machines (FSMs) are the backbone of control logic in digital design.
- **Simulation:** Always simulate your design with a testbench before synthesizing for hardware. Tools like Icarus Verilog (free), ModelSim, or Verilator let you verify logic before touching hardware.

**5. Explore community resources:**
- Nandland.com — Excellent beginner FPGA tutorials
- FPGA4Fun — Practical FPGA project guides
- Asic-World — Comprehensive Verilog/VHDL reference
- GitHub — Thousands of open-source FPGA projects to study`,
  },
];

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  console.log('=== Populating Amiga Knowledge (KS 1) ===');
  for (const doc of amigaDocs) {
    await addDoc(1, doc.title, doc.content);
  }

  console.log('\n=== Populating Gowin FPGA Knowledge (KS 2) ===');
  for (const doc of gowinDocs) {
    await addDoc(2, doc.title, doc.content);
  }

  console.log('\n=== Populating Atari 8-bit Knowledge (KS 3) ===');
  for (const doc of atariDocs) {
    await addDoc(3, doc.title, doc.content);
  }

  console.log('\n=== Populating Geek Help Knowledge (KS 4) ===');
  for (const doc of geekDocs) {
    await addDoc(4, doc.title, doc.content);
  }

  console.log('\nDone! All knowledge sets populated.');
}

main().catch(console.error);
