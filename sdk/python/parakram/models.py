from dataclasses import dataclass, field
from typing import Optional


@dataclass
class DeviceInfo:
    id: str
    name: str
    ip: str
    port: int = 8080
    api_key: str = ""
    via_relay: bool = False


@dataclass
class Capability:
    id: str
    name: str
    description: str
    available: bool = True


@dataclass
class SensorEvent:
    sensor: str
    value: float
    timestamp: int
    unit: str = ""
