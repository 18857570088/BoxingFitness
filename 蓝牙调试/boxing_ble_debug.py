import argparse
import asyncio
import csv
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from bleak import BleakClient, BleakScanner


DEVICE_NAME = "BOXING##000003"
FRAME_HEADER = b"\xD5\x5D"
FRAME_LEN = 23
DEFAULT_READ_UUID = "FFE1"
DEFAULT_NOTIFY_UUID = "FFE4"
DEFAULT_CSV = "boxing_ble_log.csv"


@dataclass
class BoxingPacket:
    timestamp: str
    raw_hex: str
    command: int
    battery: int
    charging: int
    ax: int
    ay: int
    az: int
    roll: int
    pitch: int
    yaw: int
    pressure: int
    punch_count: int
    reserved_1: int
    reserved_2: int
    crc8: int
    crc8_calc: int
    crc8_ok: bool


def normalize_uuid(value: str) -> str:
    uuid = value.strip().lower()
    if len(uuid) == 4:
        return f"0000{uuid}-0000-1000-8000-00805f9b34fb"
    return uuid


def hex_bytes(data: bytes | bytearray) -> str:
    return " ".join(f"{byte:02X}" for byte in data)


def int16_le(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset : offset + 2], "little", signed=True)


def uint16_le(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset : offset + 2], "little", signed=False)


def crc8(data: bytes) -> int:
    """CRC-8/MAXIM style fallback.

    The DOCX only says "crc8校验码" and does not name the polynomial. This value is
    logged for comparison; raw frames are still accepted when it does not match.
    """
    crc = 0x00
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 0x80:
                crc = ((crc << 1) ^ 0x31) & 0xFF
            else:
                crc = (crc << 1) & 0xFF
    return crc


def parse_packet(frame: bytes) -> BoxingPacket:
    if len(frame) != FRAME_LEN:
        raise ValueError(f"frame length must be {FRAME_LEN}, got {len(frame)}")
    if frame[:2] != FRAME_HEADER:
        raise ValueError(f"bad frame header: {frame[:2].hex(' ').upper()}")

    crc_calc = crc8(frame[:-1])
    return BoxingPacket(
        timestamp=datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
        raw_hex=hex_bytes(frame),
        command=frame[2],
        battery=frame[3],
        charging=frame[4],
        ax=int16_le(frame, 5),
        ay=int16_le(frame, 7),
        az=int16_le(frame, 9),
        roll=int16_le(frame, 11),
        pitch=int16_le(frame, 13),
        yaw=int16_le(frame, 15),
        pressure=uint16_le(frame, 17),
        punch_count=frame[19],
        reserved_1=frame[20],
        reserved_2=frame[21],
        crc8=frame[22],
        crc8_calc=crc_calc,
        crc8_ok=frame[22] == crc_calc,
    )


def append_csv(path: str, packet: BoxingPacket) -> None:
    csv_path = Path(path)
    is_new = not csv_path.exists()
    with csv_path.open("a", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=list(packet.__dict__.keys()))
        if is_new:
            writer.writeheader()
        writer.writerow(packet.__dict__)


def extract_frames(buffer: bytearray) -> list[bytes]:
    frames: list[bytes] = []
    while True:
        header_at = buffer.find(FRAME_HEADER)
        if header_at < 0:
            buffer.clear()
            return frames
        if header_at > 0:
            del buffer[:header_at]
        if len(buffer) < FRAME_LEN:
            return frames
        frames.append(bytes(buffer[:FRAME_LEN]))
        del buffer[:FRAME_LEN]


async def scan_device(timeout: float, name: str) -> str | None:
    print(f"Scanning BLE devices for {timeout:.1f}s, target name: {name}")
    devices = await BleakScanner.discover(timeout=timeout, return_adv=True)
    matches: list[tuple[str, str, int | None]] = []

    for address, (device, adv_data) in devices.items():
        local_name = device.name or adv_data.local_name or ""
        if local_name == name:
            matches.append((address, local_name, adv_data.rssi))
        elif name.lower() in local_name.lower():
            matches.append((address, local_name, adv_data.rssi))

    if not matches:
        print("No matching BLE device found.")
        return None

    for index, (address, local_name, rssi) in enumerate(matches, start=1):
        print(f"{index}. {local_name} | {address} | RSSI {rssi}")

    if len(matches) == 1:
        return matches[0][0]

    choice = input("Select device number: ").strip()
    if not choice:
        return None
    return matches[int(choice) - 1][0]


async def print_services(client: BleakClient) -> None:
    services = client.services
    print("\nGATT services and characteristics:")
    for service in services:
        print(f"- {service.uuid} | {service.description}")
        for char in service.characteristics:
            props = ",".join(char.properties)
            print(f"  - {char.uuid} | handle={char.handle} | {props}")


async def run(args: argparse.Namespace) -> None:
    address = args.address or await scan_device(args.timeout, args.name)
    if not address:
        return

    read_uuid = normalize_uuid(args.read_uuid)
    notify_uuid = normalize_uuid(args.notify_uuid)
    notify_buffer = bytearray()

    print(f"\nConnecting: {address}")
    async with BleakClient(address) as client:
        print(f"Connected: {client.is_connected}")
        await print_services(client)

        if args.read:
            try:
                data = await client.read_gatt_char(read_uuid)
                print(f"\nRead {read_uuid}: {hex_bytes(data)}")
            except Exception as exc:
                print(f"\nRead {read_uuid} failed: {exc}")

        def on_notify(sender: int, data: bytearray) -> None:
            print(f"\nNotify handle={sender}: {hex_bytes(data)}")
            notify_buffer.extend(data)
            for frame in extract_frames(notify_buffer):
                try:
                    packet = parse_packet(frame)
                except ValueError as exc:
                    print(f"Parse failed: {exc}")
                    continue

                print(
                    "Parsed: "
                    f"cmd=0x{packet.command:02X}, battery={packet.battery}%, "
                    f"charge={packet.charging}, ax={packet.ax}, ay={packet.ay}, az={packet.az}, "
                    f"roll={packet.roll}, pitch={packet.pitch}, yaw={packet.yaw}, "
                    f"pressure={packet.pressure}, punches={packet.punch_count}, "
                    f"crc=0x{packet.crc8:02X}, crc_calc=0x{packet.crc8_calc:02X}, "
                    f"crc_ok={packet.crc8_ok}"
                )
                append_csv(args.output_csv, packet)

        await client.start_notify(notify_uuid, on_notify)
        print(f"\nNotify enabled on {notify_uuid}. Listening for {args.seconds:.1f}s...")
        await asyncio.sleep(args.seconds)
        await client.stop_notify(notify_uuid)
        print(f"Done. CSV log: {Path(args.output_csv).resolve()}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="BOXING BLE debug tool")
    parser.add_argument("--name", default=DEVICE_NAME, help="BLE device name")
    parser.add_argument("--address", default="", help="BLE address, skips scan when set")
    parser.add_argument("--timeout", type=float, default=8.0, help="scan timeout seconds")
    parser.add_argument("--read-uuid", default=DEFAULT_READ_UUID, help="read characteristic UUID")
    parser.add_argument("--notify-uuid", default=DEFAULT_NOTIFY_UUID, help="notify characteristic UUID")
    parser.add_argument("--seconds", type=float, default=60.0, help="notify listen seconds")
    parser.add_argument("--output-csv", default=DEFAULT_CSV, help="CSV output path")
    parser.add_argument("--read", action="store_true", help="read read-uuid once after connect")
    return parser.parse_args()


def main() -> None:
    asyncio.run(run(parse_args()))


if __name__ == "__main__":
    main()
