"""ต้องมี rsvg-convert (librsvg) — รัน: python3 hardware/tools/gen_images.py
สร้างภาพประกอบฮาร์ดแวร์ MotoDash Hub (SVG -> PNG ด้วย rsvg-convert)"""
import subprocess, pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "img"
OUT.mkdir(parents=True, exist_ok=True)

BG = "#0B0D10"; PANEL = "#12161B"; LINE = "#2E3640"; TEXT = "#F2F4F7"; MUTED = "#AEB7C2"
AMBER = "#FFB020"; BLUE = "#4FC3F7"; RED = "#FF4D4D"; GREEN = "#7CE38B"; ORANGE = "#FF8A3D"
FONT = "DejaVu Sans, Loma, sans-serif"

def svg(w, h, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" font-family="{FONT}">
<defs>
 <marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="{MUTED}"/></marker>
 <marker id="aA" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="{AMBER}"/></marker>
 <marker id="aB" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="{BLUE}"/></marker>
 <marker id="aG" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="{GREEN}"/></marker>
</defs>
<rect width="{w}" height="{h}" fill="{BG}"/>
{body}
</svg>'''

def box(x, y, w, h, title, sub="", color=LINE, fill=PANEL, ts=15):
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{fill}" stroke="{color}" stroke-width="2"/>'
    ty = y + h / 2 + (0 if sub else 5)
    s += f'<text x="{x+w/2}" y="{ty - (8 if sub else 0)}" fill="{TEXT}" font-size="{ts}" font-weight="bold" text-anchor="middle">{title}</text>'
    if sub:
        for i, line in enumerate(sub.split("\n")):
            s += f'<text x="{x+w/2}" y="{ty + 12 + i*15}" fill="{MUTED}" font-size="12" text-anchor="middle">{line}</text>'
    return s

def arrow(x1, y1, x2, y2, color=MUTED, m="a", label="", dash=False, lx=None, ly=None):
    d = ' stroke-dasharray="6 5"' if dash else ""
    s = f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="2.5" marker-end="url(#{m})"{d}/>'
    if label:
        s += f'<text x="{lx if lx is not None else (x1+x2)/2}" y="{ly if ly is not None else (y1+y2)/2 - 6}" fill="{color}" font-size="11" text-anchor="middle">{label}</text>'
    return s

def path(d, color, m=None, dash=False):
    me = f' marker-end="url(#{m})"' if m else ""
    ds = ' stroke-dasharray="6 5"' if dash else ""
    return f'<path d="{d}" fill="none" stroke="{color}" stroke-width="2.5"{me}{ds}/>'

def title(t, sub=""):
    s = f'<text x="30" y="42" fill="{TEXT}" font-size="24" font-weight="bold">{t}</text>'
    if sub: s += f'<text x="30" y="66" fill="{MUTED}" font-size="13">{sub}</text>'
    return s

def legend(x, y, items):
    s = ""
    for i, (c, t) in enumerate(items):
        s += f'<line x1="{x}" y1="{y+i*20}" x2="{x+28}" y2="{y+i*20}" stroke="{c}" stroke-width="3"/>'
        s += f'<text x="{x+36}" y="{y+i*20+4}" fill="{MUTED}" font-size="12">{t}</text>'
    return s

# ---------------------------------------------------------------- 1) Block diagram
b = title("MotoDash Hub — System block diagram", "One USB-C cable to the phone: charging + 2 analog cameras + multi-brand OBD")
# left: vehicle side
b += box(30, 110, 190, 70, "Vehicle 12 V", "ACC (ignition) + GND\n9–16 V, load-dump", AMBER)
b += box(30, 230, 190, 70, "AHD camera 1", "front · 720p/1080p\n12 V, coax video", BLUE)
b += box(30, 320, 190, 70, "AHD camera 2", "rear · 720p/1080p\n12 V, coax video", BLUE)
b += box(30, 430, 190, 90, "Vehicle diag port", "OBD2-16 / Yamaha 4p /\nHonda / Kawasaki / Suzuki\nvia adapter cable", GREEN)
# board outline
b += f'<rect x="260" y="95" width="700" height="450" rx="16" fill="none" stroke="{AMBER}" stroke-width="2" stroke-dasharray="10 6"/>'
b += f'<text x="276" y="118" fill="{AMBER}" font-size="13" font-weight="bold">MotoDash Hub PCB (100 × 70 mm, 4-layer)</text>'
b += box(290, 130, 200, 80, "Input protection", "fuse · TVS · reverse-polarity\nideal diode (LM74700-Q1)", AMBER)
b += box(530, 130, 180, 80, "Buck 12→5 V 6 A", "LM61460-Q1\n+ LDO 3.3 V", AMBER)
b += box(290, 240, 200, 60, "Camera power 12 V", "polyfuse + LC filter ×2", AMBER, ts=14)
b += box(530, 232, 180, 76, "AHD→USB UVC ×2", "capture modules\nMJPEG 720p/1080p", BLUE)
b += box(290, 420, 200, 100, "OBD interpreter", "STN2120 (ELM327 cmds)\nCAN: TJA1051T/3\nK-line: L9637D", GREEN)
b += box(530, 430, 180, 80, "USB-UART", "CP2102N\n(app already supports)", GREEN)
b += box(750, 290, 180, 100, "USB 2.0 hub", "USB2514B (4-port MTT)\n1 cam1 · 2 cam2\n3 OBD · 4 spare", MUTED)
b += box(750, 140, 180, 100, "USB-C PD", "TPS65987D\nsource 5 V/3 A\nUFP after DR_Swap", AMBER)
b += box(1010, 220, 180, 150, "Samsung S21 FE", "USB-C\nphone = USB host\n+ charging (sink)\nMotoDash app", TEXT, fill="#171C22")
# arrows power
b += arrow(220, 145, 290, 170, AMBER, "aA")
b += arrow(490, 170, 530, 170, AMBER, "aA")
b += arrow(390, 210, 390, 240, AMBER, "aA")
b += arrow(710, 170, 750, 180, AMBER, "aA", "5 V")
b += path("M 220 265 L 290 265", AMBER); b += path("M 220 355 L 260 355 L 260 285 L 290 285", AMBER)
b += f'<text x="232" y="257" fill="{AMBER}" font-size="11">12 V</text>'
# video
b += path("M 220 250 C 380 205, 450 255, 530 258", BLUE, "aB")
b += path("M 220 340 C 400 360, 460 300, 530 285", BLUE, "aB")
b += f'<text x="400" y="333" fill="{BLUE}" font-size="11">CVBS/AHD coax</text>'
b += arrow(710, 270, 750, 320, BLUE, "aB", "USB ×2", lx=735, ly=286)
# obd
b += arrow(220, 475, 290, 470, GREEN, "aG", "CAN H/L · K · L", lx=255, ly=460)
b += arrow(490, 470, 530, 470, GREEN, "aG", "UART")
b += path("M 710 470 L 840 470 L 840 390", GREEN, "aG")
# to phone
b += arrow(930, 190, 1010, 260, AMBER, "aA", "VBUS 5 V · CC", lx=1000, ly=200)
b += arrow(930, 340, 1010, 310, MUTED, "a", "USB D+/D−", lx=968, ly=345)
b += path("M 840 290 L 840 240", MUTED, dash=True)
b += legend(1010, 420, [(AMBER, "power"), (BLUE, "video"), (GREEN, "OBD / diagnostics"), (MUTED, "USB data")])
open(OUT / "block.svg", "w").write(svg(1210, 570, b))

# ---------------------------------------------------------------- 2) Schematic sheets
def pin(x, y, name, side="l"):
    if side == "l":
        return f'<line x1="{x-24}" y1="{y}" x2="{x}" y2="{y}" stroke="{MUTED}" stroke-width="1.6"/><text x="{x+5}" y="{y+4}" fill="{TEXT}" font-size="10">{name}</text>'
    return f'<line x1="{x}" y1="{y}" x2="{x+24}" y2="{y}" stroke="{MUTED}" stroke-width="1.6"/><text x="{x-5}" y="{y+4}" fill="{TEXT}" font-size="10" text-anchor="end">{name}</text>'

def ic(x, y, w, h, ref, part, left=(), right=(), color=TEXT):
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{PANEL}" stroke="{color}" stroke-width="2"/>'
    s += f'<text x="{x+w/2}" y="{y-8}" fill="{color}" font-size="12" font-weight="bold" text-anchor="middle">{ref} {part}</text>'
    for i, n in enumerate(left): s += pin(x, y + 22 + i*20, n, "l")
    for i, n in enumerate(right): s += pin(x + w, y + 22 + i*20, n, "r")
    return s

def wire(pts, color=MUTED):
    return f'<polyline points="{" ".join(f"{a},{b}" for a,b in pts)}" fill="none" stroke="{color}" stroke-width="1.8"/>'

def comp(x, y, label, val, color=MUTED, vertical=False):
    if vertical:
        s = f'<rect x="{x-7}" y="{y}" width="14" height="34" fill="none" stroke="{color}" stroke-width="1.8"/>'
        s += f'<text x="{x+12}" y="{y+15}" fill="{TEXT}" font-size="10">{label}</text><text x="{x+12}" y="{y+28}" fill="{MUTED}" font-size="9">{val}</text>'
    else:
        s = f'<rect x="{x}" y="{y-7}" width="34" height="14" fill="none" stroke="{color}" stroke-width="1.8"/>'
        s += f'<text x="{x+17}" y="{y-12}" fill="{TEXT}" font-size="10" text-anchor="middle">{label}</text><text x="{x+17}" y="{y+22}" fill="{MUTED}" font-size="9" text-anchor="middle">{val}</text>'
    return s

def gnd(x, y):
    return f'<line x1="{x}" y1="{y}" x2="{x}" y2="{y+10}" stroke="{MUTED}" stroke-width="1.6"/><line x1="{x-8}" y1="{y+10}" x2="{x+8}" y2="{y+10}" stroke="{MUTED}" stroke-width="1.6"/><line x1="{x-5}" y1="{y+14}" x2="{x+5}" y2="{y+14}" stroke="{MUTED}" stroke-width="1.6"/>'

def sheet_frame(x, y, w, h, name, color):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="none" stroke="{color}" stroke-width="1.5" stroke-dasharray="8 5"/>'
            f'<text x="{x+14}" y="{y+22}" fill="{color}" font-size="14" font-weight="bold">{name}</text>')

s = title("MotoDash Hub — Schematic overview", "Key nets and parts (concept). Final schematic &amp; values follow each datasheet's reference design.")
# --- Sheet A: power
s += sheet_frame(20, 90, 760, 300, "A · Power input &amp; regulation", AMBER)
s += f'<text x="40" y="135" fill="{TEXT}" font-size="11">J1 ACC +12 V</text>'
s += wire([(40, 160), (90, 160)], AMBER)
s += comp(90, 160, "F1", "3 A fuse", AMBER)
s += wire([(124, 160), (170, 160)], AMBER)
s += comp(170, 175, "D1", "SM8S33A TVS", AMBER, vertical=True); s += gnd(170, 215)
s += wire([(170, 160), (230, 160)], AMBER)
s += ic(230, 140, 120, 70, "U1", "LM74700-Q1", ["IN", "GND"], ["GATE", "OUT"], AMBER)
s += wire([(374, 162), (420, 162)], AMBER)
s += f'<text x="378" y="182" fill="{AMBER}" font-size="10">+12V_PROT</text>'
s += ic(440, 140, 140, 90, "U2", "LM61460-Q1", ["VIN", "EN", "PGND"], ["SW", "FB", "BOOT"], AMBER)
s += wire([(420, 162), (416, 162)], AMBER)
s += comp(610, 162, "L1", "4.7 µH", AMBER)
s += wire([(604, 162), (610, 162)], AMBER); s += wire([(644, 162), (700, 162)], AMBER)
s += comp(700, 177, "C", "4×22 µF", AMBER, vertical=True); s += gnd(700, 217)
s += f'<text x="660" y="150" fill="{AMBER}" font-size="11">+5V (6 A)</text>'
s += ic(470, 280, 120, 60, "U3", "TLV75533 LDO", ["IN 5V", "GND"], ["OUT 3V3"], AMBER)
s += wire([(446, 302), (420, 302), (420, 250), (720, 250), (720, 162)], AMBER)
s += f'<text x="604" y="320" fill="{AMBER}" font-size="11">+3V3 → hub, STN, CP2102N</text>'
s += f'<text x="40" y="270" fill="{TEXT}" font-size="11">+12V_PROT → camera rail:</text>'
s += comp(60, 300, "PF1/PF2", "500 mA polyfuse", AMBER); s += comp(140, 300, "L2/L3", "10 µH + 100 µF", AMBER)
s += f'<text x="40" y="350" fill="{MUTED}" font-size="10">CAM1_12V / CAM2_12V (filtered, per camera)</text>'
# --- Sheet B: OBD
s += sheet_frame(800, 90, 600, 300, "B · Multi-protocol OBD (ELM327-compatible)", GREEN)
s += ic(960, 140, 150, 170, "U4", "STN2120", ["CAN_TX", "CAN_RX", "ISO_K_TX", "ISO_L_TX", "ISO_RX", "ANALOG_IN"], ["UART_TX", "UART_RX", "RESET", "OSC", "VDD 3V3", "VSS"], GREEN)
s += ic(820, 140, 80, 60, "U5", "TJA1051T/3", ["TXD", "RXD"], ["CANH", "CANL"], GREEN)
s += ic(820, 240, 80, 70, "U6", "L9637D", ["TX", "RX", "LI"], ["K", "L"], GREEN)
s += wire([(936, 162), (930, 162)], GREEN)
s += f'<text x="822" y="350" fill="{TEXT}" font-size="11">J4 OBD (DLC-16 pinout):</text>'
s += f'<text x="822" y="368" fill="{MUTED}" font-size="10">6 CAN-H · 14 CAN-L · 7 K · 15 L · 4/5 GND · 16 +12V(sense)</text>'
s += f'<text x="1150" y="140" fill="{MUTED}" font-size="10">+ ESD: SM24CANB on CAN,</text>'
s += f'<text x="1150" y="155" fill="{MUTED}" font-size="10">SMBJ33A on K/L, 120 Ω opt.</text>'
s += ic(1270, 200, 110, 70, "U7", "CP2102N", ["RXD", "TXD"], ["D+", "D−"], GREEN)
s += wire([(1134, 162), (1200, 162), (1200, 222), (1246, 222)], GREEN)
s += wire([(1134, 182), (1210, 182), (1210, 242), (1246, 242)], GREEN)
s += f'<text x="1270" y="300" fill="{GREEN}" font-size="11">→ hub port 3</text>'
# --- Sheet C: USB / video
s += sheet_frame(20, 410, 1380, 300, "C · USB-C (power + data), hub, video capture", BLUE)
s += ic(80, 470, 150, 130, "J2", "USB-C receptacle", ["VBUS", "CC1", "CC2", "D+", "D−", "GND"], [], BLUE)
s += ic(330, 470, 160, 110, "U8", "TPS65987D", ["PP5V (5V in)", "C_CC1", "C_CC2", "SPI flash"], ["PP_HV/VBUS", "I2C"], AMBER)
s += wire([(254, 492), (290, 492), (290, 530), (306, 530)], AMBER)
s += wire([(254, 512), (300, 512), (300, 550), (306, 550)], AMBER)
s += f'<text x="330" y="610" fill="{MUTED}" font-size="10">Config: Source 5 V/3 A, initiate DR_Swap → UFP</text>'
s += f'<text x="330" y="625" fill="{MUTED}" font-size="10">(phone stays USB host while charging)</text>'
s += ic(600, 470, 170, 150, "U9", "USB2514B", ["USBUP_DP", "USBUP_DM", "XTAL 24 MHz", "VDD33"], ["DN1 → CAM1", "DN2 → CAM2", "DN3 → CP2102N", "DN4 → spare"], MUTED)
s += wire([(254, 532), (560, 532), (560, 492), (576, 492)], MUTED)
s += wire([(254, 552), (570, 552), (570, 512), (576, 512)], MUTED)
s += f'<text x="520" y="660" fill="{MUTED}" font-size="10">90 Ω diff pairs · USBLC6-2SC6 ESD on each port</text>'
s += ic(880, 450, 290, 70, "M1", "AHD→USB UVC module", ["AHD in (1.25 mm 4p)"], ["USB 5V/D+/D−/GND"], BLUE)
s += ic(880, 560, 290, 70, "M2", "AHD→USB UVC module", ["AHD in (1.25 mm 4p)"], ["USB 5V/D+/D−/GND"], BLUE)
s += wire([(794, 492), (850, 492), (850, 420), (1210, 420), (1210, 472), (1194, 472)], BLUE)
s += wire([(794, 512), (860, 512), (860, 540), (1220, 540), (1220, 582), (1194, 582)], BLUE)
s += f'<text x="1232" y="480" fill="{TEXT}" font-size="11">J5 CAM1 (GX12-4):</text>'
s += f'<text x="1232" y="496" fill="{MUTED}" font-size="10">1 +12V · 2 GND · 3 Video · 4 Audio/NC</text>'
s += f'<text x="1232" y="590" fill="{TEXT}" font-size="11">J6 CAM2 (GX12-4): same</text>'
s += f'<text x="1232" y="606" fill="{MUTED}" font-size="10">75 Ω coax, shield → GND</text>'
open(OUT / "schematic.svg", "w").write(svg(1440, 730, s))

# ---------------------------------------------------------------- 3) PCB render (concept)
S = 9  # px per mm  (board 100 x 70 mm)
ox, oy = 110, 120
W, H = 100 * S, 70 * S
p = title("MotoDash Hub — PCB concept render (top)", "100 × 70 mm · 4-layer · component placement concept (not a fabrication layout)")
p += f'<rect x="{ox+6}" y="{oy+8}" width="{W}" height="{H}" rx="18" fill="#000" opacity="0.45"/>'
p += f'<rect x="{ox}" y="{oy}" width="{W}" height="{H}" rx="18" fill="#0F4D32" stroke="#1E7A50" stroke-width="3"/>'
# traces (decorative, follow nets)
def tr(pts, w=3, c="#1E7A50"):
    return f'<polyline points="{" ".join(f"{ox+a*S},{oy+b*S}" for a,b in pts)}" fill="none" stroke="{c}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>'
p += tr([(8, 12), (22, 12), (30, 12), (44, 12)], 9)
p += tr([(54, 12), (66, 12), (66, 22), (80, 22)], 9)
p += tr([(66, 22), (66, 35), (62, 35)], 6)
for k in range(4):
    p += tr([(60, 40 + k*1.2), (75, 40 + k*1.2), (88, 34 + k*1.2)], 2)
p += tr([(48, 30), (52, 38), (58, 40)], 2); p += tr([(48, 48), (52, 44), (58, 43)], 2)
p += tr([(25, 55), (35, 55), (40, 56)], 2); p += tr([(46, 58), (55, 58), (58, 46)], 2)
p += tr([(12, 40), (12, 60), (20, 60)], 3)
# mounting holes
for (hx, hy) in [(4, 4), (96, 4), (4, 66), (96, 66)]:
    p += f'<circle cx="{ox+hx*S}" cy="{oy+hy*S}" r="{2.2*S}" fill="#C9A227"/><circle cx="{ox+hx*S}" cy="{oy+hy*S}" r="{1.4*S}" fill="{BG}"/>'

def part(x, y, w, h, ref, label, fill="#14171C", stroke="#3A414B", txt=TEXT, fs=11, pins=0):
    X, Y, Wp, Hp = ox+x*S, oy+y*S, w*S, h*S
    s = ""
    if pins:  # QFN pads
        n = pins
        for i in range(n):
            t = (i + 0.5) / n
            s += f'<rect x="{X + t*Wp - 3}" y="{Y - 5}" width="6" height="5" fill="#C9C9C9"/>'
            s += f'<rect x="{X + t*Wp - 3}" y="{Y + Hp}" width="6" height="5" fill="#C9C9C9"/>'
            s += f'<rect x="{X - 5}" y="{Y + t*Hp - 3}" width="5" height="6" fill="#C9C9C9"/>'
            s += f'<rect x="{X + Wp}" y="{Y + t*Hp - 3}" width="5" height="6" fill="#C9C9C9"/>'
    s += f'<rect x="{X}" y="{Y}" width="{Wp}" height="{Hp}" rx="4" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>'
    s += f'<text x="{X+Wp/2}" y="{Y+Hp/2-2}" fill="{txt}" font-size="{fs}" font-weight="bold" text-anchor="middle">{label}</text>'
    s += f'<text x="{X+Wp/2}" y="{Y+Hp/2+12}" fill="#9AA4B2" font-size="9" text-anchor="middle">{ref}</text>'
    return s

def silk(x, y, t, fs=11, anchor="start"):
    return f'<text x="{ox+x*S}" y="{oy+y*S}" fill="#E8E8E8" font-size="{fs}" text-anchor="{anchor}">{t}</text>'

# connectors on edges
p += part(-3, 7, 9, 12, "J1", "12V", fill="#1F7A3A", stroke="#2FA052", fs=12)        # screw terminal
p += silk(1, 22, "ACC / GND", 10)
p += part(-3, 32, 10, 22, "J4", "OBD", fill="#2A2E35", stroke=GREEN, fs=12)         # OBD header
p += silk(1, 57, "OBD 16-pin", 10)
p += part(20, -4, 12, 12, "J5", "CAM1", fill="#3A3F47", stroke=BLUE, fs=11)
p += part(36, -4, 12, 12, "J6", "CAM2", fill="#3A3F47", stroke=BLUE, fs=11)
p += f'<circle cx="{ox+26*S}" cy="{oy+2*S}" r="{3.6*S}" fill="none" stroke="#C9C9C9" stroke-width="2"/>'
p += f'<circle cx="{ox+42*S}" cy="{oy+2*S}" r="{3.6*S}" fill="none" stroke="#C9C9C9" stroke-width="2"/>'
p += part(94, 30, 9, 10, "J2", "USB-C", fill="#B8BCC2", stroke="#E0E0E0", txt="#0B0D10", fs=10)
p += silk(92, 46, "TO PHONE", 10, "end")
# power section
p += part(9, 9, 5, 5, "F1", "F", fill="#D9D2B0", stroke="#B8B090", txt="#222", fs=9)
p += part(16, 9, 6, 6, "D1", "TVS", fs=9)
p += part(24, 9, 7, 6, "U1", "LM74700", fs=8, pins=4)
p += part(33, 8, 6, 8, "Q1", "MOSFET", fs=8)
p += part(44, 7, 10, 10, "U2", "LM61460", fs=9, pins=6)
p += part(56, 7, 9, 9, "L1", "4.7µH", fill="#2B2B2B", stroke="#555", fs=9)
for k in range(4):
    p += part(56 + k*3.3, 18, 2.6, 4, "", "", fill="#B88A4A", stroke="#8C6A38")
p += part(70, 20, 7, 5, "U3", "LDO", fs=9, pins=3)
p += part(14, 26, 6, 6, "PF1", "PF", fill="#C9A227", stroke="#8C7A20", txt="#222", fs=9)
p += part(22, 26, 6, 6, "PF2", "PF", fill="#C9A227", stroke="#8C7A20", txt="#222", fs=9)
p += silk(14, 35, "CAM 12V", 9)
# PD + hub
p += part(80, 22, 10, 10, "U8", "TPS65987D", fs=8, pins=7)
p += part(80, 34, 6, 4, "U10", "FLASH", fs=8)
p += part(62, 36, 11, 11, "U9", "USB2514B", fs=8, pins=7)
p += part(62, 49, 5, 3, "Y1", "24MHz", fill="#C9C9C9", stroke="#999", txt="#222", fs=7)
# OBD section
p += part(14, 40, 12, 12, "U4", "STN2120", fs=9, pins=8)
p += part(29, 39, 7, 5, "U5", "TJA1051", fs=8, pins=3)
p += part(29, 47, 7, 5, "U6", "L9637D", fs=8, pins=3)
p += part(38, 53, 8, 8, "U7", "CP2102N", fs=8, pins=5)
p += part(14, 56, 6, 3, "Y2", "XTAL", fill="#C9C9C9", stroke="#999", txt="#222", fs=7)
# capture modules (daughterboards on headers)
p += part(40, 22, 20, 12, "M1", "AHD→UVC #1", fill="#1B3A5C", stroke=BLUE, fs=11)
p += part(76, 50, 20, 12, "M2", "AHD→UVC #2", fill="#1B3A5C", stroke=BLUE, fs=11)
p += part(48, 63, 8, 4, "J7", "SPARE USB", fill="#2A2E35", stroke=MUTED, fs=7)
# LEDs
for i, c in enumerate([RED, AMBER, GREEN]):
    p += f'<circle cx="{ox+(86+i*3.5)*S}" cy="{oy+5*S}" r="{1*S}" fill="{c}"/>'
p += silk(85, 9.5, "PWR  USB  OBD", 8)
p += silk(4, 68.5, "MotoDash Hub v0.1", 12)
# legend / callouts
lx = ox + W + 60
items = [("J1", "12 V ACC input (screw terminal)"), ("F1/D1/U1", "fuse · TVS · reverse-polarity"),
         ("U2 + L1", "buck 12→5 V, 6 A"), ("U3", "LDO 3.3 V"), ("PF1/PF2", "camera 12 V polyfuses"),
         ("J5/J6", "AHD camera inputs (GX12-4)"), ("M1/M2", "AHD→USB UVC capture modules"),
         ("U4–U6", "STN2120 + CAN + K-line"), ("U7", "USB-UART CP2102N"), ("U9", "USB 2.0 hub (4 ports)"),
         ("U8", "USB-C PD source / UFP"), ("J2", "USB-C to phone"), ("J4", "OBD 16-pin (brand adapters)")]
p += f'<text x="{lx}" y="{oy+10}" fill="{TEXT}" font-size="15" font-weight="bold">Parts</text>'
for i, (r, t) in enumerate(items):
    p += f'<text x="{lx}" y="{oy+38+i*24}" fill="{AMBER}" font-size="12" font-weight="bold">{r}</text>'
    p += f'<text x="{lx+82}" y="{oy+38+i*24}" fill="{MUTED}" font-size="12">{t}</text>'
open(OUT / "pcb.svg", "w").write(svg(ox + W + 400, oy + H + 70, p))

for n in ["block", "schematic", "pcb"]:
    subprocess.run(["rsvg-convert", "-z", "1.5", str(OUT / f"{n}.svg"), "-o", str(OUT / f"{n}.png")], check=True)
print("ok")
