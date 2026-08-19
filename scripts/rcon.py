#!/usr/bin/env python3
"""Minimal RCON client — gradle cannot pipe stdin to the dev server console, so this is the
only way to drive it live. Usage: rcon.py "command" ["command" ...]"""
import socket, struct, sys

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "standards-dev"
LOGIN, COMMAND, RESPONSE = 3, 2, 0


def pack(req_id, req_type, body):
    payload = struct.pack("<ii", req_id, req_type) + body.encode("utf8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def read(sock):
    raw = sock.recv(4)
    if len(raw) < 4:
        return None, ""
    size = struct.unpack("<i", raw)[0]
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            break
        data += chunk
    req_id, _ = struct.unpack("<ii", data[:8])
    return req_id, data[8:-2].decode("utf8", "replace")


def main(commands):
    with socket.create_connection((HOST, PORT), timeout=10) as sock:
        sock.sendall(pack(1, LOGIN, PASSWORD))
        req_id, _ = read(sock)
        if req_id == -1:
            print("!! RCON auth failed")
            return 1
        for n, command in enumerate(commands, start=2):
            sock.sendall(pack(n, COMMAND, command))
            _, body = read(sock)
            print(f"> {command}")
            if body.strip():
                print(body.strip())
            print()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
